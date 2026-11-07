package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reward_dispute")
public class RewardDispute extends BaseEntity {
    private Long postId;

    private Long userId;

    private String content;

    private String evidence;

    private String status;

    private String decision;

    private Long adminId;

}
