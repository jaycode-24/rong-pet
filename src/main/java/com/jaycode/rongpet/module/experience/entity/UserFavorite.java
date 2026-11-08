package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户帖子收藏关系，对应表 t_user_favorite。
 * (user_id, post_id) 唯一（uk_favorite_pair）；取消收藏走逻辑删除，
 * 再次收藏时由 Mapper 恢复原记录，避免唯一键冲突。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_favorite")
public class UserFavorite extends BaseEntity {

    /** 收藏用户 ID */
    private Long userId;

    /** 被收藏帖子 ID */
    private Long postId;

}
