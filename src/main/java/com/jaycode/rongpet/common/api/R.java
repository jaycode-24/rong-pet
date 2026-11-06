package com.jaycode.rongpet.common.api;

import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应体：{ code, message, data, traceId }。
 * 成功 code 固定为 "0"，业务失败使用模块错误码。
 */
@Data
public class R<T> implements Serializable {

    private String code;
    private String message;
    private T data;
    private String traceId;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = CommonErrorCode.SUCCESS.getCode();
        r.message = CommonErrorCode.SUCCESS.getMessage();
        r.data = data;
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> R<T> fail(String code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.traceId = MDC.get("traceId");
        return r;
    }
}
