package com.jaycode.rongpet.module.account.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员信息视图。密码哈希等敏感字段绝不外泄。
 */
@Data
public class AdminUserVO {

    private Long id;

    private String username;

    private String realName;

    /** SUPER/AUDITOR */
    private String role;

    /** ENABLED/DISABLED */
    private String status;

    private LocalDateTime lastLoginTime;
}
