package com.jaycode.rongpet.module.experience.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jaycode.rongpet.module.experience.entity.Feedback;
import org.apache.ibatis.annotations.*;

public interface FeedbackMapper extends BaseMapper<Feedback> {
    @Select("SELECT * FROM t_feedback WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Feedback lock(@Param("id") long id);
}
