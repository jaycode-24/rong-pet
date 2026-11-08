package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反馈的运营回复，对应表 t_feedback_reply。
 * 一条反馈可有多条回复；写入回复的同时把 t_feedback.status 置为 REPLIED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feedback_reply")
public class FeedbackReply extends BaseEntity {

    /** 关联反馈 ID */
    private Long feedbackId;

    /** 回复人（后台管理员）ID */
    private Long adminId;

    /** 回复内容 */
    private String content;

}
