package com.cyxy.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 配送状态。
 *
 * 状态流转：
 *   NOT_SET（没填预计送达时间）
 *     → IN_TRANSIT（填了预计送达、还没确认收货，即「配送中」）
 *     → EARLY / ON_TIME / LATE（确认送达时按天数差判定，此后锁定）
 *     → 撤销确认则回到 IN_TRANSIT 或 NOT_SET
 *
 * 判定口径：实际送达日期 − 预计送达日期，按自然日比较，同一天算准时。
 * 时间统一 Asia/Shanghai，由 ProductService 显式指定时区，不依赖 JVM 默认值。
 *
 * @author: Koreyoshi
 * @version: 1.0
 * @date: 2026年11月03日, 12:40:00
 */
@Getter
public enum DeliveryStatus {

    /** 未填写预计送达时间 */
    NOT_SET("NOT_SET", "未填写"),

    /** 配送中：已填预计送达时间，尚未确认送达 */
    IN_TRANSIT("IN_TRANSIT", "配送中"),

    /** 提前送达：实际送达日期早于预计 */
    EARLY("EARLY", "提前送达"),

    /** 准时送达：实际送达日期与预计同一天 */
    ON_TIME("ON_TIME", "准时送达"),

    /** 晚送达：实际送达日期晚于预计 */
    LATE("LATE", "晚送达");

    private final String code;
    private final String label;

    DeliveryStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按 code 反查枚举，认不出就当未填写（历史脏数据兜底） */
    public static DeliveryStatus of(String code) {
        if (code == null || code.isBlank()) return NOT_SET;
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(NOT_SET);
    }

    /**
     * 按天数差判定送达结果。
     *
     * @param delayDays 实际送达日期 − 预计送达日期（自然日）
     * @return 负数 EARLY / 0 ON_TIME / 正数 LATE
     */
    public static DeliveryStatus judge(int delayDays) {
        if (delayDays < 0) return EARLY;
        if (delayDays > 0) return LATE;
        return ON_TIME;
    }

    /** 是否已完成配送（确认送达后的三种终态） */
    public boolean isDelivered() {
        return this == EARLY || this == ON_TIME || this == LATE;
    }
}
