package com.jaycode.rongpet.module.account.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

/**
 * 账号模块错误码。
 */
@Getter
public enum AccountErrorCode implements ErrorCode {

    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    USER_BANNED("USER_BANNED", "账号已被封禁或注销，请联系客服"),
    WX_NOT_CONFIGURED("WX_NOT_CONFIGURED", "微信登录暂未开通，请稍后再试"),
    ADMIN_NOT_FOUND("ADMIN_NOT_FOUND", "管理员不存在"),
    ADMIN_DISABLED("ADMIN_DISABLED", "账号已停用，请联系管理员"),
    ADMIN_CREDENTIALS_INVALID("ADMIN_CREDENTIALS_INVALID", "用户名或密码错误"),
    ADMIN_LOGIN_LOCKED("ADMIN_LOGIN_LOCKED", "密码错误次数过多，账号已锁定30分钟");

    private final String code;
    private final String message;

    AccountErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
