package com.jaycode.rongpet.module.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台管理员，对应表 t_admin_user。
 * 密码存 BCrypt 加盐哈希；连续 5 次错误锁定 30 分钟。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admin_user")
public class AdminUser extends BaseEntity {

    private String username;

    /** BCrypt 加盐哈希 */
    private String password;

    private String realName;

    /** SUPER 超级管理员 / AUDITOR 审核员 */
    private String role;

    /** ENABLED/DISABLED */
    private String status;

    /** 登录锁定截止时间 */
    private LocalDateTime lockUntil;

    private LocalDateTime lastLoginTime;
}
