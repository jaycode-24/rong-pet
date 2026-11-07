package com.jaycode.rongpet.module.admin.dto;

import lombok.Data;

/**
 * 后台通用操作请求：审核结论、原因、凭证等。
 */
@Data
public class AdminActionRequest {

    /** 审核是否通过 */
    private Boolean approve;

    /** 原因 / 备注 / 驳回说明 */
    private String reason;

    /** 转账凭证号 */
    private String voucherNo;

    /** 人工置顶天数 */
    private Integer days;

    /** 用户处置动作：LIMIT_POST/LIMIT_TRADE/BAN/UNBAN */
    private String action;

    /** 处置期限（天），仅记录于说明 */
    private Integer durationDays;

    /** 目标ID，按接口语义使用 */
    private Long targetId;

    /** 操作类型，按接口语义使用 */
    private String operation;
}
