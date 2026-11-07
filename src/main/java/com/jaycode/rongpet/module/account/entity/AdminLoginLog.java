package com.jaycode.rongpet.module.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员登录日志，对应表 t_admin_login_log。
 * append-only 表：无逻辑删除与乐观锁列，只插入不更新，用于安全审计。
 */
@Data
@TableName("t_admin_login_log")
public class AdminLoginLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 管理员ID，账号不存在时为空 */
    private Long adminId;

    private String username;

    private String loginIp;

    private String userAgent;

    /** 结果：1成功 0失败 */
    private Integer loginResult;

    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
