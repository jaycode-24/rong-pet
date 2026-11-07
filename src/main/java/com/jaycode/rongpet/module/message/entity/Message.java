package com.jaycode.rongpet.module.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内消息，对应表 t_message。四类：SYSTEM/AUDIT/ORDER/FUND。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_message")
public class Message extends BaseEntity {

    private Long userId;

    private String type;

    private String title;

    private String content;

    private String bizType;

    private String bizNo;

    private Integer isRead;

    private LocalDateTime readTime;
}
