package com.jaycode.rongpet.common.error;

import lombok.Getter;

/**
 * 全局通用错误码。模块专属错误码在各模块内另行定义。
 */
@Getter
public enum CommonErrorCode implements ErrorCode {

    SUCCESS("0", "成功"),
    BAD_REQUEST("BAD_REQUEST", "请求参数有误"),
    UNAUTHORIZED("UNAUTHORIZED", "登录已失效，请重新登录"),
    FORBIDDEN("FORBIDDEN", "没有操作权限"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    CONFLICT("CONFLICT", "资源状态冲突，请刷新后重试"),
    RATE_LIMITED("RATE_LIMITED", "操作过于频繁，请稍后再试"),
    REMOTE_CALL_ERROR("REMOTE_CALL_ERROR", "外部服务调用失败，请稍后重试"),
    CRYPTO_ERROR("CRYPTO_ERROR", "数据加解密失败"),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统繁忙，请稍后重试");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
