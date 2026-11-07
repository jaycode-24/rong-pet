package com.jaycode.rongpet.module.gratuity.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum GratuityErrorCode implements ErrorCode {

    NOT_CONFIRMED_CLAIM("GRATUITY_NOT_CONFIRMED_CLAIM", "请在认领申请被确认后再发放感谢金"),
    AMOUNT_INVALID("GRATUITY_AMOUNT_INVALID", "感谢金金额不在允许范围内"),
    NOT_FOUND("GRATUITY_NOT_FOUND", "感谢金记录不存在"),
    STATUS_INVALID("GRATUITY_STATUS_INVALID", "感谢金状态不允许该操作"),
    ALREADY_PAID("GRATUITY_ALREADY_PAID", "该帖子已发放感谢金");

    private final String code;
    private final String message;

    GratuityErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
