package com.jaycode.rongpet.module.gratuity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GratuityVO {

    private Long id;

    private Long postId;

    private String postTitle;

    private Long payerId;

    private Long amount;

    private Long fee;

    private Long netAmount;

    /** 脱敏收款账户 */
    private String payeeAccountMasked;

    private String orderNo;

    private String status;

    private String refundReason;

    private LocalDateTime paidTime;

    private LocalDateTime createTime;
}
