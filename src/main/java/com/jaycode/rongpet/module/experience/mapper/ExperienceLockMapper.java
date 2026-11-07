package com.jaycode.rongpet.module.experience.mapper;

import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.pet.entity.Pet;
import org.apache.ibatis.annotations.*;

/** 跨模块共享锁顺序：先帖子／宠物，再锁其业务对象。 */
public interface ExperienceLockMapper {
    @Select("SELECT * FROM t_post WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Post lockPost(@Param("id") long id);

    @Select("SELECT * FROM t_pet WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Pet lockPet(@Param("id") long id);

    @Update("UPDATE t_post SET refund_notice_time=NOW(),update_time=NOW(),version=version+1 WHERE id=#{id} AND refund_notice_time IS NULL AND is_deleted=0")
    int claimReminder(@Param("id") long id);
}
