package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyxy.mapper.DictExchangeRateMapper;
import com.cyxy.mapper.SysConfigMapper;
import com.cyxy.model.dto.RateQuote;
import com.cyxy.model.entity.DictExchangeRate;
import com.cyxy.model.entity.SysConfig;
import com.cyxy.model.vo.ExchangeRateVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 汇率同步：一次事务里同时写「历史表」和「当前值」，对外只暴露一个当前值读入口。
 *
 * <p><b>职责分工（用户 2026-11-10 定案）</b>：</p>
 * <ul>
 *   <li>{@code dict_exchange_rate} 是时序表，只记录汇率变化、只喂 ECharts 趋势图，一天一条；</li>
 *   <li>{@code sys_config.DEFAULT_EXCHANGE_RATE}（中文名「最新汇率」）是当前生效值，
 *       <b>所有业务读它的唯一入口</b>，利润测算 / prefill 都不许自己去历史表 ORDER BY 取最新；</li>
 *   <li>本类负责把两边写一致，别的地方只读、不写。</li>
 * </ul>
 *
 * <p><b>为什么必须同一个事务</b>：如果先写历史表成功、再更新全局参数失败，
 * 就会出现「趋势图有今天的新值、测算还在用昨天的旧值」。@Transactional 让两者同成功同失败，
 * 失败时调用方保留上一次的汇率继续用，绝不影响测算页。</p>
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateSyncService.class);

    /** 全局参数里「最新汇率」的键。⚠️ 只改中文名，键本身不要动，Java/前端/脚本都按它查 */
    public static final String CONFIG_KEY = "DEFAULT_EXCHANGE_RATE";

    private static final String CURRENCY_PAIR = "USD/CNY";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 超过 3 个自然日没更新就提示「数据可能过期」，只做提示不阻断 */
    private static final long STALE_DAYS = 3;

    private final DictExchangeRateMapper rateMapper;
    private final SysConfigMapper sysConfigMapper;
    private final ExchangeRateClient exchangeRateClient;
    private final ProfitDictLoader profitDictLoader;

    /**
     * 当前生效汇率 + 溯源信息，供 GET /api/exchange-rate/latest 和 refresh 共用。
     * rate 走 ProfitDictLoader（读 sys_config），其余字段只是给用户看「这个值哪来的」。
     *
     * <p><b>updateTime 必须重新查一次 sys_config</b>：全局参数页和利润测算页的
     * 「修改时间」都以这一行的 updated_time 为准，不能用内存里的对象，也不能用
     * 历史表的 fetched_at —— 前者可能刚被 insert 还没回填，后者是「数据拉取时间」，
     * 用户点按钮后即使拉到同一个汇率，也应该看到本次动作留下的新时间。</p>
     */
    public ExchangeRateVO latest() {
        DictExchangeRate newest = newestRow();
        SysConfig config = sysConfigMapper.selectById(CONFIG_KEY);
        boolean stale = newest == null || newest.getRateDate() == null
                || ChronoUnit.DAYS.between(
                        newest.getRateDate(), LocalDate.now(BUSINESS_ZONE)) > STALE_DAYS;
        return new ExchangeRateVO(
                profitDictLoader.latestUsdCnyRate(),
                newest == null ? null : newest.getRateDate(),
                newest == null ? null : newest.getSource(),
                newest == null ? null : newest.getProvider(),
                newest == null ? null : newest.getFetchedAt(),
                config == null ? null : config.getUpdatedTime(),
                stale);
    }

    /**
     * 主动同步一次（定时任务或页面「同步最新汇率」按钮）。
     * 外部接口失败时抛异常，由 Controller 交给全局异常处理；库里的旧值原样保留。
     */
    public ExchangeRateVO syncFromRemote() {
        RateQuote quote = exchangeRateClient.fetch();
        applyQuote(quote, "API");
        return latest();
    }

    /**
     * 把一条汇率落到两处。写成 public 且接受 RateQuote，是为了让单测不用联网也能验证
     * 「同一天重复写不新增行」「两处值一致」这两条关键约束。
     *
     * @param source API 接口拉取 / MANUAL 人工录入
     */
    @Transactional
    public void applyQuote(RateQuote quote, String source) {
        if (quote == null || quote.rate() == null || quote.rateDate() == null) {
            throw new IllegalArgumentException("汇率或业务日期为空，拒绝写入");
        }

        // 【1】历史表：按 (currency_pair, rate_date) 唯一键 UPSERT。
        // 用原生 ON DUPLICATE KEY UPDATE 而不是「先查后写」：实体带 @TableLogic，
        // selectOne 会自动追加 is_deleted=0，软删过的行查不到 → 走 insert → 撞唯一键。
        // upsert 同时把 is_deleted 复位为 0，天然绕开这个坑（项目里 product_fba_fee 同款写法）。
        DictExchangeRate row = new DictExchangeRate();
        row.setCurrencyPair(CURRENCY_PAIR);
        row.setRate(quote.rate());
        row.setRateDate(quote.rateDate());
        row.setSource(source);
        row.setProvider(quote.provider());
        row.setFetchedAt(LocalDateTime.now());
        row.setRemark("汇率同步任务写入");
        rateMapper.upsert(row);

        // 【2】全局参数：只更新 value + 修改时间，绝不碰 config_name / remark ——
        // 那两个是给人看的中文说明，管理员改过之后不能被同步任务覆盖回去。
        //
        // ⚠️ 必须显式写 updatedTime：表上的 ON UPDATE CURRENT_TIMESTAMP 只在「行内容真的变了」
        // 时才触发。若这次拉到的汇率和上次一样（一天内很常见），MySQL 认为没变化，
        // 时间戳就不动，用户点「获取最新汇率」会看不到任何反馈，以为按钮坏了。
        // 显式赋值既是合法 SQL，也能保证每次点击都留下本次动作的修改时间。
        SysConfig config = new SysConfig();
        config.setConfigKey(CONFIG_KEY);
        config.setConfigValue(quote.rate().toPlainString());
        config.setUpdatedTime(LocalDateTime.now(BUSINESS_ZONE));
        if (sysConfigMapper.updateById(config) == 0) {
            // 全新空库可能压根没预置这一项，补一条最小可用配置，避免测算永远走兜底值
            config.setValueType("NUMBER");
            config.setConfigName("最新汇率");
            config.setRemark("USD/CNY 当前生效汇率；业务读取的唯一入口");
            sysConfigMapper.insert(config);
        }

        // 【3】汇率本身不进 ProfitDictLoader 的字典缓存（每次直查 sys_config），
        // 但其他字典是缓存的；统一 refresh 一次，保证同一次同步后所有字典口径一起刷新。
        profitDictLoader.refresh();
        log.info("汇率已同步：{} @ {} 来源 {} / {}", quote.rate(), quote.rateDate(), source, quote.provider());
    }

    /** 最新一条历史记录，只用于展示溯源，不参与业务取值 */
    private DictExchangeRate newestRow() {
        List<DictExchangeRate> rows = rateMapper.selectList(
                new LambdaQueryWrapper<DictExchangeRate>()
                        .eq(DictExchangeRate::getCurrencyPair, CURRENCY_PAIR)
                        .orderByDesc(DictExchangeRate::getRateDate)
                        .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
