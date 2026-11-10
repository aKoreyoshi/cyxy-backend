package com.cyxy.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author: Koreyoshi
 * @description: 尾程退货处理费字典，对应 dict_return_processing_fee 表
 *
 * <p>一张表同时放两种形态的行，与 {@link DictFbaStepRate} 的思路一致：</p>
 * <ul>
 *   <li>FLAT：固定值档，费用直接读 feeUsd；</li>
 *   <li>FORMULA：原表里写成「$5.00 + $0.05/4 盎司（超出首重 3 磅的部分）」这类文字，
 *       这里拆成 baseFeeUsd / stepFeeUsd / stepUnit / stepFromOz 四个可计算字段，
 *       同时留 formulaText 原文给参数列表页逐字展示。</li>
 * </ul>
 *
 * <p>7 个分组由业务口径给定（原表没有分组标题行）：
 * 小号标准尺寸 8 档、大号标准尺寸 13 档、大号大件、超大尺寸 0-50磅、
 * 超大件 50-70 / 70-150 / 150磅以上。tierCode 把它们归成 small / large /
 * oversize 三个大类，groupCode + groupName 保留细分口径供展示页分组。</p>
 *
 * <p>取值只依据计费重：标准尺寸由计算器自动算出的大类收窄（不需要用户填），
 * 大件不再按尺寸细分、纯粹按计费重落在哪个区间取；「大号大件」与
 * 「超大尺寸：0-50磅」区间重叠时按表中顺序取第一条，值仍允许手改。</p>
 *
 * <p>为什么重量统一用 oz：利润计算器的计费重是 lb，查表时乘 16 即可，
 * 避免库里混用 lb / oz 两套单位导致边界判断出错。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_return_processing_fee")
public class DictReturnProcessingFee extends BaseEntity {

    /** 大类：small 小号标准 / large 大号标准 / oversize 大件（含 4 个大件分组） */
    private String tierCode;

    /** 分组编码：SMALL_STANDARD / LARGE_STANDARD / LARGE_BULKY / OVERSIZE_* */
    private String groupCode;

    /** 分组名称，原表没有标题行，由业务口径给定，展示页按它分组 */
    private String groupName;

    /** 同大类内的匹配顺序；大号大件与超大尺寸同为 0~50lb 时取序号最小的一行 */
    private Integer sortNo;

    /** 重量下界 oz（左开） */
    private BigDecimal weightMinOz;

    /** 重量上界 oz（右闭），NULL 表示不限 */
    private BigDecimal weightMaxOz;

    /** 原表区间文字，展示用，如「2 至 4 盎司（不含 2 盎司）」 */
    private String weightLabel;

    /** FLAT 固定值 / FORMULA 起步价+续重 */
    private String feeType;

    /** 固定尾程退货费 $/件，仅 FLAT 有值 */
    private BigDecimal feeUsd;

    /** 起步价 $，仅 FORMULA 有值 */
    private BigDecimal baseFeeUsd;

    /** 每个步进单位的加价 $，仅 FORMULA 有值 */
    private BigDecimal stepFeeUsd;

    /** 步进单位：OZ4 每 4 盎司 / LB 每磅 */
    private String stepUnit;

    /** 续重起算点 oz：只有超出这部分的重量才累加续重费 */
    private BigDecimal stepFromOz;

    /** 原表公式文字，展示用 */
    private String formulaText;

    /** 备注（含原表标注的 2027-02-01 生效口径说明） */
    private String remark;

    /** 生效起始日；当前留空，见迁移脚本文件头说明 */
    private LocalDate validFrom;

    /** 生效截止日 */
    private LocalDate validTo;
}
