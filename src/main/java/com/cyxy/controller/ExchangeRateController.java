package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.model.vo.ExchangeRateVO;
import com.cyxy.service.ExchangeRateSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 汇率对外接口。
 *
 * <p><b>为什么要单独一组接口，而不是塞进 /api/dict</b>：/api/dict/exchange-rate 返回的是
 * 整张时序表（给趋势图和记录表格用），这里返回的是「当前生效值」这一个概念，
 * 来源是 sys_config 而不是历史表。两者语义不同，混在一起以后很容易有人图省事
 * 从列表里取最后一条当当前值，又把两套口径搅回去。</p>
 *
 * <p><b>为什么 refresh 是 POST 却不登记到菜单写接口清单</b>：AuthInterceptor 只会拦
 * 「路径命中 sys_menu.write_api_patterns」的请求。这个接口只做系统级数据同步、
 * 不增删改任何业务数据，性质等同 /api/profit/calc（POST 但只算不写库）；
 * 若登记进参数列表菜单，只读角色会被挡成 403，反而用不了同步按钮。</p>
 */
@RestController
@RequestMapping("/api/exchange-rate")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateSyncService syncService;

    /** 当前生效汇率 + 数据日期/来源，进页面读一次即可，不要前端定时轮询 */
    @GetMapping("/latest")
    public Result<ExchangeRateVO> latest() {
        return Result.ok(syncService.latest());
    }

    /**
     * 手动触发一次同步（主源→备源→失败保留旧值），返回更新后的当前值与修改时间。
     *
     * <p><b>为什么两个按钮共用这一个接口</b>：全局参数页「最新汇率」行的「获取最新汇率」、
     * 利润测算页汇率输入框的「取最新」，以及汇率变化页的「同步最新汇率」，
     * 对使用者来说是同一个动作 —— 拿到最新汇率并让全局参数生效。
     * 只保留一条后端链路，三处看到的汇率和「修改时间」必然一致，也不会出现
     * 「A 按钮读历史表、B 按钮打外部接口」这种两套口径。</p>
     *
     * <p>使用者不是开发者，不需要知道汇率存在哪张表：按钮一律让后端去外部接口取真·最新值，
     * 一个事务里同时写历史表和全局参数。定时任务仍按 09:30 / 23:30 原样执行，不受影响。</p>
     *
     * <p>失败会由全局异常处理返回业务错误码，前端提示「已保留当前汇率」，不阻断测算。</p>
     */
    @PostMapping("/refresh")
    public Result<ExchangeRateVO> refresh() {
        return Result.ok(syncService.syncFromRemote());
    }
}
