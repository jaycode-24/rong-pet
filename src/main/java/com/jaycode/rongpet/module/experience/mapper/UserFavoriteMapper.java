package com.jaycode.rongpet.module.experience.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jaycode.rongpet.module.experience.entity.UserFavorite;
import org.apache.ibatis.annotations.*;

public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
    @Insert("INSERT INTO t_user_favorite(id,user_id,post_id,create_by,update_by) VALUES (#{id},#{user},#{post},#{user},#{user}) ON DUPLICATE KEY UPDATE is_deleted=0,update_time=NOW(),update_by=#{user},version=version+1")
    int restoreOrInsert(@Param("id") long id, @Param("user") long user, @Param("post") long post);
}
