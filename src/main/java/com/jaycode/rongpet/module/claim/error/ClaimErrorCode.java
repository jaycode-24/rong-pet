package com.jaycode.rongpet.module.claim.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum ClaimErrorCode implements ErrorCode {

    CLAIM_NOT_FOUND("CLAIM_NOT_FOUND", "认领申请不存在"),
    CLAIM_LIMIT("CLAIM_LIMIT", "该帖认领申请数量已达上限"),
    CLAIM_DUPLICATE("CLAIM_DUPLICATE", "您已提交过认领申请，请等待处理"),
    CLAIM_SELF("CLAIM_SELF", "不能认领自己发布的招领帖"),
    CLAIM_POST_NOT_FOUND("CLAIM_POST_NOT_FOUND", "招领帖不存在或当前不可认领"),
    CLAIM_STATUS_INVALID("CLAIM_STATUS_INVALID", "当前认领状态不允许该操作"),
    CLAIM_ONLY_OWNER("CLAIM_ONLY_OWNER", "仅拾宠人可处理认领申请"),
    CLAIM_MATERIAL_REQUIRED("CLAIM_MATERIAL_REQUIRED", "请至少上传2项证明材料"),
    CLAIM_NOT_CONFIRMED("CLAIM_NOT_CONFIRMED", "请先确认认领申请后再确认归还");

    private final String code;
    private final String message;

    ClaimErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
