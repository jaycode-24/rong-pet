package com.jaycode.rongpet.module.admin.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 运营报表（PRD 11.3）：日报/周报/月报。
 * 金额一律为分，导出字段与页面展示保持同一口径。
 */
@Data
public class StatsReportVO {

    /** DAY/WEEK/MONTH */
    private String periodType;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 生成时间 */
    private String generatedAt;

    // ---------------- 日报核心指标 ----------------
    /** 区间新增用户 */
    private long newUsers;
    /** 区间新增帖子 */
    private long newPosts;
    /** 区间新增喂养请求 */
    private long newFeedingRequests;
    /** 区间确认收入合计（分，悬赏服务费+置顶费+感谢金抽成） */
    private long incomeCents;
    /** 区间退款金额（分） */
    private long refundCents;
    /** 区间异常数（举报 + 审核超时 + 退款失败） */
    private long exceptionCount;

    // ---------------- 周报扩展指标 ----------------
    /** 新增认证喂养员（供给变化） */
    private long newCertifiedFeeders;
    /** 区间悬赏下单数 */
    private long rewardOrders;
    /** 区间悬赏支付成功数 */
    private long rewardPaidOrders;
    /** 区间分账转付方案数 */
    private long splitPaidCount;
    /** 区间分账转付金额（分） */
    private long splitPaidAmount;

    // ---------------- 月报扩展指标 ----------------
    /** 收入结构：悬赏服务费（分） */
    private long rewardFeeIncome;
    /** 收入结构：置顶费（分） */
    private long topIncome;
    /** 收入结构：感谢金抽成（分） */
    private long gratuityFeeIncome;
    /** 区间已找回帖子数 */
    private long foundPosts;
    /** 区间结束的寻宠/招领帖数 */
    private long endedPosts;
    /** 找回率（百分比，保留两位） */
    private double foundRate;
    /** 区间被举报内容数 */
    private long reportedCount;
    /** 区间封禁/处置账号数 */
    private long punishedUsers;

    /** 明细表行（导出与页面一致的字段集合） */
    private List<Row> rows;

    /**
     * 报表明细行：一行一个指标，导出与页面共用，避免两套口径。
     */
    @Data
    public static class Row {
        private String metric;
        private String value;
        private String unit;
        private String remark;

        public Row(String metric, String value, String unit, String remark) {
            this.metric = metric;
            this.value = value;
            this.unit = unit;
            this.remark = remark;
        }
    }
}
