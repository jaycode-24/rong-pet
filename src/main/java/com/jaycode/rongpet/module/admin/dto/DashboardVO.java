package com.jaycode.rongpet.module.admin.dto;

import lombok.Data;

/**
 * 后台数据看板指标（PRD 7.2）：用户/内容/供给/交易/找回/收入/风险。
 * 金额统一为分，前端按需转元展示。
 */
@Data
public class DashboardVO {

    // ---------------- 用户 ----------------
    private long totalUsers;
    private long todayNewUsers;
    private long limitedUsers;
    private long bannedUsers;

    // ---------------- 内容 ----------------
    private long totalPosts;
    private long todayNewPosts;
    private long pendingAuditPosts;
    private long showingPosts;
    private long completedPosts;
    private long pendingClaims;
    private long pendingReports;

    // ---------------- 供给 ----------------
    private long certifiedFeeders;
    private long activeFeeders;
    private long pendingFeederApplies;

    // ---------------- 交易 ----------------
    /** 当前托管中的悬赏本金（分） */
    private long rewardFrozenAmount;
    /** 累计已分账悬赏本金（分） */
    private long rewardPaidAmount;
    /** 累计平台服务费收入（分） */
    private long serviceFeeIncome;
    /** 累计感谢金平台抽成（分） */
    private long gratuityFeeIncome;
    /** 累计退款金额（分） */
    private long refundAmount;
    /** 今日支付成功单数 */
    private long todayPaidOrders;

    // ---------------- 找回 ----------------
    /** 已找回帖子数 */
    private long foundPosts;
    /** 找回率（已找回 / 可找回帖子，百分比，保留两位） */
    private double foundRate;

    // ---------------- 风险 ----------------
    /** 待审核超过 4 小时的帖子 */
    private long overdueAuditPosts;
    /** 待核验分账方案数 */
    private long splitWaitReview;
    /** 认证已过期的喂养员数 */
    private long expiredFeeders;
}
