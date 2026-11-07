package com.jaycode.rongpet.module.account.dto;

import lombok.Data;

/**
 * 后台登录响应。
 */
@Data
public class AdminLoginVO {

    /** Sa-Token 凭证，前端放入 Authorization 头 */
    private String token;

    private AdminUserVO admin;
}
