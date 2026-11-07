package com.jaycode.rongpet.module.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台操作审计日志（append-only），保留不少于 3 年，不允许后台删除。
 */
@Data
@TableName("t_admin_audit_log")
public class AdminAuditLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long adminId;

    private String username;

    private String action;

    private String targetType;

    private Long targetId;

    private String detail;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
