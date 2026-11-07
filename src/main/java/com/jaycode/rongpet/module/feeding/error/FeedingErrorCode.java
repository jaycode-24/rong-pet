package com.jaycode.rongpet.module.feeding.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum FeedingErrorCode implements ErrorCode {

    NOT_FOUND("FEEDING_NOT_FOUND", "喂养请求不存在"),
    STATUS_INVALID("FEEDING_STATUS_INVALID", "当前请求状态不允许该操作"),
    ONLY_PARTICIPANT("FEEDING_ONLY_PARTICIPANT", "仅请求双方可操作"),
    NO_PERMISSION("FEEDING_NO_PERMISSION", "无权查看该喂养请求"),
    DISTRICT_INVALID("FEEDING_DISTRICT_INVALID", "服务地址仅支持成都行政区县"),
    DISTRICT_NOT_SERVED("FEEDING_DISTRICT_NOT_SERVED", "该喂养员暂不服务所选区县"),
    SERVICE_TYPE_INVALID("FEEDING_SERVICE_TYPE_INVALID", "服务类型不合法"),
    TIME_INVALID("FEEDING_TIME_INVALID", "服务时间不合法"),
    AGREEMENT_REQUIRED("FEEDING_AGREEMENT_REQUIRED", "请先阅读并勾选《上门喂养服务须知》"),
    USER_FORBIDDEN("FEEDING_USER_FORBIDDEN", "当前账号状态不可发布喂养请求");

    private final String code;
    private final String message;

    FeedingErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
