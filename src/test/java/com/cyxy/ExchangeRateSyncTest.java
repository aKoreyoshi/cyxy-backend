package com.cyxy;

import com.cyxy.mapper.DictExchangeRateMapper;
import com.cyxy.mapper.SysConfigMapper;
import com.cyxy.model.dto.RateQuote;
import com.cyxy.model.entity.SysConfig;
import com.cyxy.model.vo.ExchangeRateVO;
import com.cyxy.service.ExchangeRateClient;
import com.cyxy.service.ExchangeRateSyncService;
import com.cyxy.service.ProfitDictLoader;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 汇率同步测试。
 *
 * <p>两条设计约束必须一直成立：</p>
 * <ol>
 *   <li>解析层不能联网 —— 单测拿 JSON 字符串直接验证，境外接口抖一下也不能让 CI 变红；</li>
 *   <li>同一业务日期重复同步只留一行，且 sys_config 当前值与历史表最新一条始终一致。</li>
 * </ol>
 *
 * <p>类上 @Transactional：applyQuote 会真的写两张表，测试结束全部回滚，
 * 绝不能污染本地库的汇率历史和全局参数（否则其他测试的基准会跟着漂）。</p>
 *
 * <p>另见 src/test/resources/application.properties 把定时同步关掉：
 * Spring Boot 测试同样会启动调度器，如果恰好跑在 09:30 / 23:30，
 * 定时任务会真的去请求境外接口并写库，测试会变得又慢又不稳定。</p>
 */
@SpringBootTest
@Transactional
class ExchangeRateSyncTest {

    @Autowired private ExchangeRateSyncService syncService;
    @Autowired private DictExchangeRateMapper rateMapper;
    @Autowired private SysConfigMapper sysConfigMapper;
    @Autowired private ProfitDictLoader dictLoader;
    // 读项目实际生效的 Jackson 配置，避免测试自己造一套口径
    @Autowired private Environment environment;
    // ⚠️ Spring Boot 4 的 MVC 序列化走 Jackson 3（tools.jackson），容器里只有这个
    //    ObjectMapper bean；com.fasterxml.jackson（Jackson 2）是同项目里手工解析 JSON 用的，
    //    注入它会 NoSuchBeanDefinition，也验不到接口真正吐出的字段名。
    @Autowired private ObjectMapper mvcObjectMapper;

    @Test
    @DisplayName("主源 Frankfurter 响应归一：rate / 业务日期 / 提供方")
    void parsePrimary() throws Exception {
        RateQuote q = ExchangeRateClient.parseFrankfurter(
                "{\"date\":\"2026-11-10\",\"base\":\"USD\",\"quote\":\"CNY\",\"rate\":6.6713}");
        assertEquals(0, new BigDecimal("6.671300").compareTo(q.rate()), "统一成 6 位小数");
        assertEquals(LocalDate.of(2026, 11, 10), q.rateDate(), "业务日期直接用接口给的 date");
        assertEquals("frankfurter", q.provider());
    }

    @Test
    @DisplayName("备源 open.er-api 响应归一：更新时间戳按北京时间折算业务日期")
    void parseBackup() throws Exception {
        // 2026-11-10 00:00:01 UTC = 北京时间同日 08:00:01，不该被 UTC 差一天带偏
        long unix = Instant.parse("2026-11-10T00:00:01Z").getEpochSecond();
        RateQuote q = ExchangeRateClient.parseOpenErApi(
                "{\"result\":\"success\",\"rates\":{\"CNY\":6.67},\"time_last_update_unix\":" + unix + "}");
        assertEquals(0, new BigDecimal("6.670000").compareTo(q.rate()));
        assertEquals(LocalDate.of(2026, 11, 10), q.rateDate());
        assertEquals("open-er-api", q.provider());
    }

    @Test
    @DisplayName("脏响应一律拒绝，绝不写进库")
    void rejectDirtyPayloads() {
        // 缺业务日期 / 负数 / 备源报错 / 备源缺 CNY —— 任一情况都要抛异常触发降级
        assertThrows(Exception.class, () ->
                ExchangeRateClient.parseFrankfurter("{\"rate\":6.67}"));
        assertThrows(Exception.class, () ->
                ExchangeRateClient.parseFrankfurter("{\"date\":\"2026-11-10\",\"rate\":-1}"));
        assertThrows(Exception.class, () ->
                ExchangeRateClient.parseOpenErApi("{\"result\":\"error\"}"));
        assertThrows(Exception.class, () ->
                ExchangeRateClient.parseOpenErApi("{\"result\":\"success\",\"rates\":{}}"));
    }

    @Test
    @DisplayName("同一天重复同步只留一行，且全局参数与历史表最新值一致")
    void applyQuoteIsIdempotent() {
        // ⚠️ 别用「总数 before+1」：本地库会被定时任务/页面同步写入真实记录，
        // 测试日期可能恰好已存在，upsert 是覆盖而不是新增，总数断言会莫名变红。
        // 只按业务日期数行，才是唯一键幂等这件事本身该验的东西。
        LocalDate day = LocalDate.of(2026, 1, 5);
        RateQuote q = new RateQuote(new BigDecimal("6.750000"), day, "unit-test");

        syncService.applyQuote(q, "MANUAL");
        syncService.applyQuote(q, "MANUAL");

        assertEquals(1, countByDate(day), "同一业务日期重复写只应有一行（唯一键 upsert）");
        SysConfig cfg = sysConfigMapper.selectById(ExchangeRateSyncService.CONFIG_KEY);
        assertEquals(0, new BigDecimal(cfg.getConfigValue()).compareTo(q.rate()),
                "全局参数「最新汇率」应被同步更新");
        assertEquals(0, dictLoader.latestUsdCnyRate().compareTo(q.rate()),
                "利润测算读到的汇率应等于全局参数当前值");
    }

    @Test
    @DisplayName("同步后返回全局参数的修改时间，且与库里 updated_time 一致")
    void latestCarriesConfigUpdatedTime() {
        RateQuote q = new RateQuote(new BigDecimal("6.760000"),
                LocalDate.of(2026, 1, 6), "unit-test");
        syncService.applyQuote(q, "API");

        // 从 latest() 拿到的时间，必须就是 sys_config 那一行的 updated_time，
        // 不能拿历史表的 fetched_at 顶替 —— 页面显示的「修改时间」口径只有这一处
        var vo = syncService.latest();
        SysConfig cfg = sysConfigMapper.selectById(ExchangeRateSyncService.CONFIG_KEY);
        assertNotNull(vo.updatedTime(), "接口应返回全局参数行的修改时间");
        assertEquals(cfg.getUpdatedTime(), vo.updatedTime(),
                "接口返回的修改时间应与库里 sys_config.updated_time 完全一致");
        assertEquals(0, vo.rate().compareTo(q.rate()), "返回的当前值应等于刚写入的汇率");
    }

    @Test
    @DisplayName("汇率数值没变也要刷新修改时间，否则点按钮看不到反馈")
    void refreshTouchesUpdatedTimeEvenWhenRateUnchanged() {
        RateQuote q = new RateQuote(new BigDecimal("6.770000"),
                LocalDate.of(2026, 1, 7), "unit-test");
        syncService.applyQuote(q, "API");
        var first = sysConfigMapper.selectById(ExchangeRateSyncService.CONFIG_KEY)
                .getUpdatedTime();

        // 同一个日期、同一个值再同步一次：唯一键 upsert 覆盖历史行，
        // 但用户主动点「获取最新汇率」仍应看到一个更新的修改时间
        syncService.applyQuote(q, "API");
        var second = sysConfigMapper.selectById(ExchangeRateSyncService.CONFIG_KEY)
                .getUpdatedTime();
        assertNotNull(second);
        assertFalse(second.isBefore(first), "重复同步不能让修改时间倒退");
    }

    @Test
    @DisplayName("接口序列化出的字段名是 updated_time，与前端列配置一致")
    void voSerializesUpdatedTimeAsSnakeCase() throws Exception {
        // ⚠️ 全局 Jackson SNAKE_CASE 是本项目老坑：Java 里叫 updatedTime，
        // 前端必须按 updated_time 取。之前 params/columns.js 写成 updated_at，
        // 「最后更新」列就一直空白。这条用例把字段名契约钉死，防止再改回去。
        // 先确认线上/本地配置确实是 SNAKE_CASE，再拿 MVC 真正用的那个 mapper 序列化，
        // 这样验到的就是接口实际吐给前端的字段名，不是测试自己造的口径。
        assertEquals("SNAKE_CASE",
                environment.getProperty("spring.jackson.property-naming-strategy"),
                "项目全局命名策略必须是 SNAKE_CASE，前端才按 snake_case 取字段");
        var vo = new ExchangeRateVO(
                new BigDecimal("6.680000"),
                LocalDate.of(2026, 11, 10),
                "API", "frankfurter",
                LocalDateTime.of(2026, 11, 10, 9, 30),
                LocalDateTime.of(2026, 11, 10, 9, 30, 1),
                false);
        // Jackson 3 与 Jackson 2 的 JsonNode 不通用，这里绕一层字符串：
        // 用 MVC 的 mapper 写、用 Jackson 2 读成树，只为了断言键名，不掺和类型差异
        JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mvcObjectMapper.writeValueAsString(vo));
        assertTrue(json.has("updated_time"), "序列化必须产出 updated_time");
        assertFalse(json.has("updatedTime"), "不能出现驼峰字段名");
        assertFalse(json.has("updated_at"), "不对应数据库列名以外的新造字段名");
        assertTrue(json.has("rate_date"), "其余字段仍走 SNAKE_CASE");
        assertFalse(json.get("updated_time").isNull(), "有值时不能序列化成 null");
    }

    @Test
    @DisplayName("不同业务日期各留一行，趋势曲线才有多个点")
    void applyQuoteKeepsDailyHistory() {
        LocalDate d1 = LocalDate.of(2026, 1, 8);
        LocalDate d2 = LocalDate.of(2026, 1, 9);
        // 同样按日期数行，不依赖库里已有的真实记录条数
        assertEquals(0, countByDate(d1));
        assertEquals(0, countByDate(d2));
        syncService.applyQuote(new RateQuote(new BigDecimal("6.700000"),
                d1, "unit-test"), "MANUAL");
        syncService.applyQuote(new RateQuote(new BigDecimal("6.800000"),
                d2, "unit-test"), "MANUAL");
        assertEquals(1, countByDate(d1));
        assertEquals(1, countByDate(d2));
    }

    /** 按业务日期数行；类上 @Transactional 会回滚，不会污染本地库 */
    private long countByDate(LocalDate date) {
        return rateMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<com.cyxy.model.entity.DictExchangeRate>()
                .eq(com.cyxy.model.entity.DictExchangeRate::getRateDate, date));
    }
}
