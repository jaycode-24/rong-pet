package com.jaycode.rongpet.module.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 支付单，对应表 t_pay_order。全部收款必须有业务单号，金额单位分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pay_order")
public class PayOrder extends BaseEntity {

    private String outTradeNo;

    /** REWARD/TOP/GRATUITY */
    private String bizType;

    private String bizNo;

    private Long userId;

    /** 本金（分） */
    private Long amount;

    /** 平台服务费（分） */
    private Long fee;

    /** 实付合计（分） */
    private Long total;

    /** CREATED/PAID/CLOSED/REFUNDING/REFUNDED/FAILED */
    private String status;

    /** MOCK/WXPAY */
    private String channel;

    private String transactionId;

    private LocalDateTime paidTime;

    private String refundNo;

    private Long refundAmount;

    private LocalDateTime refundTime;

    private String failReason;

    /** 退款失败重试次数，连续 3 次转人工线下处理 */
    private Integer refundRetryCount;
}
