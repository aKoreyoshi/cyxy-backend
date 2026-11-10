package com.cyxy.service;

import com.cyxy.model.vo.ExchangeRateVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日自动同步汇率。
 *
 * <p><b>为什么一天两次（09:30 / 23:30 北京时间）</b>：Frankfurter 用的是各家央行/官方
 * 参考价，欧洲时段约 16:00 CET 发布（北京时间约 23:00）。早上那次拿的是上一工作日的值，
 * 晚上那次拿当天的新值；两次都按接口返回的「业务日期」UPSERT，同一天只会覆盖同一行，
 * 不会把趋势曲线弄出重复点。</p>
 *
 * <p><b>为什么整段 try-catch 吞掉异常</b>：定时任务失败绝不能影响后台运行，更不能在
 * 日志里刷未捕获异常。失败时库里保留上一次的汇率，页面会显示「数据可能过期」，
 * 用户仍可手工同步或手填。这也是境外 API 在国内部署环境里必须有的降级。</p>
 *
 * <p><b>enabled 开关的用途</b>：线上服务器在国内，如果某天境外接口整体不可达，
 * 可以直接关掉自动同步（只保留页面手动同步与 sys_config 里的人工值），不用改代码重新发版。</p>
 */
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateScheduler.class);

    private final ExchangeRateSyncService syncService;

    @Value("${cyxy.exchange-rate.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${cyxy.exchange-rate.sync-cron:0 30 9,23 * * *}", zone = "Asia/Shanghai")
    public void dailySync() {
        if (!enabled) {
            log.debug("汇率自动同步已关闭（cyxy.exchange-rate.enabled=false），跳过");
            return;
        }
        try {
            ExchangeRateVO unused = syncService.syncFromRemote();
            log.info("定时同步汇率完成：{}", unused.rate());
        } catch (Exception e) {
            log.warn("定时同步汇率失败，保留上一次值：{}", e.toString());
        }
    }
}
