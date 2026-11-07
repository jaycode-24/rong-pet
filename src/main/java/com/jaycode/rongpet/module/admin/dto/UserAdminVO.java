package com.jaycode.rongpet.module.admin.dto;

import com.jaycode.rongpet.module.admin.entity.AdminAuditLog;
import com.jaycode.rongpet.module.fund.entity.FundFlow;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import com.jaycode.rongpet.module.report.entity.Report;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户列表/详情视图，敏感字段脱敏输出。
 * 详情额外带最近帖子、订单、资金流水、举报与被处置历史，减少后台多次请求。
 */
@Data
public class UserAdminVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    private String maskedPhone;

    private String realNameMasked;

    private String idCardMasked;

    private String status;

    private String city;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    private boolean banned;

    /** 发帖总数（详情返回） */
    private Long postCount;

    /** 被举报次数（详情返回） */
    private Long reportCount;

    /** 最近帖子（详情返回，最多 10 条） */
    private List<PostCardVO> recentPosts;

    /** 最近支付单（详情返回，最多 10 条） */
    private List<PayOrder> recentOrders;

    /** 最近资金流水（详情返回，最多 10 条） */
    private List<FundFlow> recentFundFlows;

    /** 最近被举报记录（详情返回，最多 10 条） */
    private List<Report> recentReports;

    /** 被处置历史（详情返回，最多 20 条） */
    private List<AdminAuditLog> punishmentHistory;
}
