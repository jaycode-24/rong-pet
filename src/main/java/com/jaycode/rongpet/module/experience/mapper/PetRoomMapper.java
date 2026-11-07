package com.jaycode.rongpet.module.experience.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jaycode.rongpet.module.experience.entity.PetRoom;
import org.apache.ibatis.annotations.*;

public interface PetRoomMapper extends BaseMapper<PetRoom> {
    @Insert("INSERT INTO t_pet_room(id,pet_id,user_id,scene,create_by,update_by) VALUES (#{id},#{pet},#{user},#{scene},#{user},#{user}) ON DUPLICATE KEY UPDATE scene=VALUES(scene),is_deleted=0,update_time=NOW(),update_by=#{user},version=version+1")
    int saveScene(@Param("id") long id,@Param("pet") long pet,@Param("user") long user,@Param("scene") String scene);
}
