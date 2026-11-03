package com.cyxy.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 佣金费率树形表格的节点。
 *
 * <p>父行 = 佣金类目（dict_category_commission），子行 = 阶梯档位（dict_category_commission_tier）。
 * 因为 el-table 树形数据要求父子行共用同一套列，所以这里把两类记录压成同一个 VO：
 * 各自有值的列填值，没有的列留空（前端统一显示为 "--"）。</p>
 *
 * <p>只有 feeMode = FULL / MARGINAL 的类目带 children，FLAT 类目是普通叶子行。</p>
 *
 * <p>注意：全局 Jackson 策略是 SNAKE_CASE，本 VO 对外字段名会自动变成
 * row_key / fee_mode / price_range / min_commission_usd / valid_from 等，
 * 前端列配置直接按 snake_case 写即可。</p>
 *
 * @author: Koreyoshi
 */
@Data
public class CommissionTreeNodeVO {

    /** 树形表格 row-key，父 "c:类目编码"，子 "t:类目编码:档位号" */
    private String rowKey;

    /** 名称：父=类目中文名，子="第 N 档" */
    private String name;

    /** 编码：父=category_code，子留空 */
    private String code;

    /** 费率模式（统一大写）：FLAT / FULL / MARGINAL，子行为 null */
    private String feeMode;

    /** 价格区间：仅子行有值，如 "$0 ~ $100" / "$100 以上" */
    private String priceRange;

    /** 费率：父=flat_rate，子=tier_rate（小数，0.15 = 15%） */
    private BigDecimal rate;

    /** 最低佣金 $ */
    private BigDecimal minCommissionUsd;

    /** 是否需要亚马逊审核：父行有值，子行 null */
    private Integer needApproval;

    /** 状态：1 启用 / 0 停用，父行有值 */
    private Integer status;

    private LocalDate validFrom;

    private LocalDate validTo;

    /** 说明原文 */
    private String remark;

    /** 阶梯档位子节点；FLAT 类目为 null */
    private List<CommissionTreeNodeVO> children;
}
