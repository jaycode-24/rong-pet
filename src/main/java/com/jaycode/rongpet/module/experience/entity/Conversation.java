package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子相关的站内沟通会话，对应表 t_conversation。
 * 围绕同一帖子，访客与发帖人之间只有一条会话（uk_conversation: post_id + guest_id 唯一），
 * 首次打开命中唯一键冲突时重查返回既有会话，保证幂等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_conversation")
public class Conversation extends BaseEntity {

    /** 关联帖子 ID */
    private Long postId;

    /** 会话主人，即发帖人用户 ID */
    private Long ownerId;

    /** 访客，即主动发起沟通的用户 ID */
    private Long guestId;

}
