package com.jaycode.rongpet.module.experience.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jaycode.rongpet.module.experience.entity.Conversation;
import org.apache.ibatis.annotations.*;

public interface ConversationMapper extends BaseMapper<Conversation> {
    @Select("SELECT * FROM t_conversation WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Conversation lock(@Param("id") long id);
}
