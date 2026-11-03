package com.cyxy.model.vo;

/**
 * @author: Koreyoshi
 * @description: 字典下拉选项，只吐 code / name 两个字段
 * 不把整张字典表暴露给前端：内部字段（阈值、费率、生效区间）没必要出去，
 * 也避免以后加字段时前端被动跟着变
 * @version: 1.0
 * @date: 2026年11月2日, 00:50:00
 */
public record DictOptionVO(String code, String name) {
}
