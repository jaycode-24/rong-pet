package com.jaycode.rongpet.module.split.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 悬赏分账方案，对应表 t_split_plan。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_split_plan")
public class SplitPlan extends BaseEntity {

    private Long postId;

    /** 参与分账本金（分） */
    private Long totalAmount;

    /** WAIT_REVIEW/APPROVED/PAID */
    private String status;

    private Integer reviewRequired;

    private LocalDateTime approveTime;

    private LocalDateTime paidTime;
}
