package com.jaycode.rongpet.module.feeder.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum FeederErrorCode implements ErrorCode {

    APPLY_PENDING("FEEDER_APPLY_PENDING", "您已有认证申请正在审核中"),
    APPLY_NOT_FOUND("FEEDER_APPLY_NOT_FOUND", "认证申请不存在"),
    APPLY_STATUS_INVALID("FEEDER_APPLY_STATUS_INVALID", "该认证申请已处理"),
    MATERIAL_REQUIRED("FEEDER_MATERIAL_REQUIRED", "请上传人脸核验或手持证件材料"),
    PROFILE_NOT_FOUND("FEEDER_PROFILE_NOT_FOUND", "喂养员资料不存在"),
    PROFILE_NOT_CERTIFIED("FEEDER_NOT_CERTIFIED", "请先完成喂养员实名认证"),
    PROFILE_STATUS_INVALID("FEEDER_STATUS_INVALID", "当前喂养员状态不允许该操作"),
    DISTRICT_INVALID("FEEDER_DISTRICT_INVALID", "服务区县仅支持成都行政区县"),
    INTRO_LENGTH_INVALID("FEEDER_INTRO_LENGTH_INVALID", "服务介绍需50-500字"),
    PRICE_INVALID("FEEDER_PRICE_INVALID", "参考价格不合法"),
    USER_FORBIDDEN("FEEDER_USER_FORBIDDEN", "当前账号状态不可申请认证"),
    NO_PERMISSION("FEEDER_NO_PERMISSION", "仅喂养员本人可操作");

    private final String code;
    private final String message;

    FeederErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
