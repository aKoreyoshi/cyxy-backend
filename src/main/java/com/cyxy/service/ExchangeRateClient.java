package com.cyxy.service;

import com.cyxy.model.dto.RateQuote;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 外部汇率接口客户端：主源 Frankfurter v2，备源 open.er-api。
 *
 * <p><b>为什么选这两家</b>：都免 API Key（服务器上不用多存一份密钥，也就没有
 * application-local.yml 再加敏感配置的麻烦），都是「每日参考价」而不是 tick 级行情。
 * 本项目的汇率是「把美元折成人民币算利润」的核算口径，不是下单交易，一天一个带日期的
 * 参考价反而更稳：同一个方案隔几分钟不会算出不同利润，也方便对账。</p>
 *
 * <p><b>为什么要备源</b>：线上服务器在国内，访问境外接口可能超时或被拦。主源挂了自动切备源，
 * 两个都挂就抛异常，由 Service 保留上一次的值 —— <b>拉不到汇率绝不能让测算页报错</b>。</p>
 *
 * <p><b>为什么解析写成 static</b>：单测要能脱离网络验证 JSON 解析与归一逻辑，
 * 不能每次跑测试都真的去请求境外 API（网络一抖 CI 就红）。fetch() 只负责发请求，
 * 解析交给下面两个纯静态方法。</p>
 */
@Component
public class ExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateClient.class);

    /** 静态 Mapper 只用于解析响应文本，不参与 Spring 的 JSON 序列化配置 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 业务日期统一按北京时间算，避免 UTC 差一天导致趋势曲线错位 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 汇率合理性上限：USD/CNY 不可能到 100，拦住接口返回脏数据污染库 */
    private static final BigDecimal MAX_REASONABLE_RATE = new BigDecimal("100");

    private final RestClient restClient;
    private final String primaryUrl;
    private final String backupUrl;

    public ExchangeRateClient(
            @Value("${cyxy.exchange-rate.primary-url:https://api.frankfurter.dev/v2/rate/usd/cny}") String primaryUrl,
            @Value("${cyxy.exchange-rate.backup-url:https://open.er-api.com/v6/latest/USD}") String backupUrl,
            @Value("${cyxy.exchange-rate.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${cyxy.exchange-rate.read-timeout-ms:3000}") long readTimeoutMs) {

        this.primaryUrl = primaryUrl;
        this.backupUrl = backupUrl;

        // 必须显式设超时：境外接口在国内偶发「连得上但不返回」，不设的话默认无限等待，
        // 会把 Tomcat 线程和数据库连接一起挂住，最后拖垮整个后台。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 取一次汇率。主源失败才打备源，两个都失败抛异常让上层降级。
     * 注意这里 catch 的是 Exception 而不是只 catch RestClientException：
     * JSON 解析失败、日期格式异常同样要触发切换，不能让一次脏响应卡死整条链路。
     */
    public RateQuote fetch() {
        try {
            String body = restClient.get().uri(primaryUrl).retrieve().body(String.class);
            RateQuote quote = parseFrankfurter(body);
            log.info("汇率主源获取成功：{} @ {} ({})", quote.rate(), quote.rateDate(), quote.provider());
            return quote;
        } catch (Exception e) {
            log.warn("汇率主源不可用（{}），切换备源：{}", primaryUrl, e.toString());
        }

        try {
            String body = restClient.get().uri(backupUrl).retrieve().body(String.class);
            RateQuote quote = parseOpenErApi(body);
            log.info("汇率备源获取成功：{} @ {} ({})", quote.rate(), quote.rateDate(), quote.provider());
            return quote;
        } catch (Exception e) {
            log.error("汇率备源也不可用（{}），本次不同步", backupUrl, e);
            throw new IllegalStateException("汇率主源与备源均不可用，已保留上一次汇率", e);
        }
    }

    /**
     * 主源 Frankfurter v2 单货币对响应：
     * {@code {"date":"2026-11-10","base":"USD","quote":"CNY","rate":6.6713}}
     */
    public static RateQuote parseFrankfurter(String json) throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode rateNode = root.path("rate");
        if (!rateNode.isNumber()) {
            throw new IllegalArgumentException("Frankfurter 响应缺少数值 rate：" + brief(json));
        }
        String dateText = root.path("date").asText("");
        if (dateText.isBlank()) {
            throw new IllegalArgumentException("Frankfurter 响应缺少业务日期 date：" + brief(json));
        }
        // 接口给的 date 是它自己的发布日，直接拿来当 rate_date 最准，
        // 不要改成本地当天，否则冬令时/时差会把趋势曲线整体平移一天。
        return checked(rateNode.decimalValue(), LocalDate.parse(dateText), "frankfurter");
    }

    /**
     * 备源 open.er-api 响应：
     * {@code {"result":"success","rates":{"CNY":6.67},"time_last_update_unix":...}}
     * 它没有明确的业务日期字段，只能用更新时间戳按北京时间折算；
     * 万一连时间戳都没有，退而用「北京时间今天」，保证 rate_date 不为空。
     */
    public static RateQuote parseOpenErApi(String json) throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(json);
        if (!"success".equals(root.path("result").asText())) {
            throw new IllegalArgumentException("open.er-api 未返回 success：" + brief(json));
        }
        JsonNode cny = root.path("rates").path("CNY");
        if (!cny.isNumber()) {
            throw new IllegalArgumentException("open.er-api 响应缺少 rates.CNY：" + brief(json));
        }
        long unix = root.path("time_last_update_unix").asLong(0L);
        LocalDate rateDate = unix > 0
                ? Instant.ofEpochSecond(unix).atZone(BUSINESS_ZONE).toLocalDate()
                : LocalDate.now(BUSINESS_ZONE);
        return checked(cny.decimalValue(), rateDate, "open-er-api");
    }

    /** 归一出口：统一精度、挡掉明显不合理的数值，两家数据源都必须过这一关 */
    private static RateQuote checked(BigDecimal rate, LocalDate rateDate, String provider) {
        if (rate == null || rate.signum() <= 0 || rate.compareTo(MAX_REASONABLE_RATE) > 0) {
            throw new IllegalArgumentException("汇率数值异常：" + rate + "（" + provider + "）");
        }
        if (rateDate == null) {
            throw new IllegalArgumentException("汇率业务日期为空（" + provider + "）");
        }
        // 与 dict_exchange_rate.rate 的 DECIMAL(12,6) 对齐，避免库里出现一长串浮点尾巴
        return new RateQuote(rate.setScale(6, RoundingMode.HALF_UP), rateDate, provider);
    }

    /** 日志里只留响应开头，避免把整份 JSON 刷进日志文件 */
    private static String brief(String json) {
        if (json == null) {
            return "<empty>";
        }
        return json.length() <= 200 ? json : json.substring(0, 200) + "...";
    }
}
