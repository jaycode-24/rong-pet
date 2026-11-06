package com.jaycode.rongpet.common.exception;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。所有可预期的业务拦截统一抛出本异常，由全局异常处理器转换为标准响应体。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
