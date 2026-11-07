package com.jaycode.rongpet.module.experience.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jaycode.rongpet.module.experience.entity.PetAvatarTask;
import org.apache.ibatis.annotations.*;

public interface PetAvatarTaskMapper extends BaseMapper<PetAvatarTask> {
    @Select("SELECT * FROM t_pet_avatar_task WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    PetAvatarTask lock(@Param("id") long id);
}
