package com.jaycode.rongpet.module.gratuity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 感谢金，对应表 t_gratuity。失主自愿支付，平台抽成（活动期免），转付前冻结。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_gratuity")
public class Gratuity extends BaseEntity {

    private Long postId;

    /** 失主（支付人 = 确认认领的申请人） */
    private Long payerId;

    private Long amount;

    private Long fee;

    private Long netAmount;

    private String payeeAccountEnc;

    private String payeeAccountHmac;

    private String orderNo;

    /** CREATED/FROZEN/PAID/REFUNDED */
    private String status;

    private LocalDateTime paidTime;

    private String refundReason;
}
