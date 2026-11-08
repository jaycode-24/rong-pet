package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内会话消息，对应表 t_conversation_message。
 * 发送前经过敏感词与联系方式/外链/私下收付款正则拦截，并限制每分钟最多 20 条；
 * 消息入库后向接收方推送一条站内通知。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_conversation_message")
public class ConversationMessage extends BaseEntity {

    /** 所属会话 ID */
    private Long conversationId;

    /** 发送人用户 ID（会话主人或访客） */
    private Long senderId;

    /** 消息文本内容 */
    private String content;

}
