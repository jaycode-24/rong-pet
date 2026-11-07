package com.jaycode.rongpet.module.account.dto;

import lombok.Data;

/**
 * 小程序登录响应。
 */
@Data
public class LoginVO {

    /** Sa-Token 凭证，客户端后续请求放入 Authorization 头 */
    private String token;

    /** 是否新用户注册，前端据此引导补充资料 */
    private boolean isNew;

    private UserVO user;
}
