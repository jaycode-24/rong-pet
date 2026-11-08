package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户意见反馈，对应表 t_feedback。
 * 用户在 App 端提交，运营在后台回复后状态置为 REPLIED，并通过站内信通知用户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feedback")
public class Feedback extends BaseEntity {

    /** 反馈用户 ID */
    private Long userId;

    /** 反馈分类：功能建议/使用问题/内容与服务/其他意见 */
    private String category;

    /** 反馈正文，5-1000 字 */
    private String content;

    /** PENDING 待处理 / REPLIED 已回复 */
    private String status;

}
