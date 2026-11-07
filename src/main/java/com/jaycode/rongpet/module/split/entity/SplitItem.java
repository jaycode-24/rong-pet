package com.jaycode.rongpet.module.split.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 悬赏分账明细，对应表 t_split_item。金额与评分在生成方案时固化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_split_item")
public class SplitItem extends BaseEntity {

    private Long planId;

    private Long postId;

    private Long clueId;

    private Long userId;

    private Integer score;

    private Long amount;

    /** UNPAID/PAID */
    private String payStatus;

    private LocalDateTime paidTime;
}
