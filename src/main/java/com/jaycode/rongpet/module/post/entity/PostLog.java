package com.jaycode.rongpet.module.post.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子状态流转日志（append-only），保留不少于 180 天。
 */
@Data
@TableName("t_post_log")
public class PostLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long postId;

    private Long operatorId;

    /** USER/ADMIN/SYSTEM */
    private String operatorType;

    private String action;

    private String fromStatus;

    private String toStatus;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
