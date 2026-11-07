package com.jaycode.rongpet.module.post.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum PostErrorCode implements ErrorCode {

    POST_NOT_FOUND("POST_NOT_FOUND", "帖子不存在"),
    POST_NOT_OWNER("POST_NOT_OWNER", "只能操作自己的帖子"),
    POST_STATUS_INVALID("POST_STATUS_INVALID", "当前帖子状态不允许该操作"),
    POST_TYPE_INVALID("POST_TYPE_INVALID", "帖子类型不合法"),
    USER_FORBIDDEN("POST_USER_FORBIDDEN", "当前账号状态不可发布帖子"),
    NEW_USER_REWARD_FORBIDDEN("NEW_USER_REWARD_FORBIDDEN", "新账号注册7日内不可发布含悬赏帖子，请先完成一次普通发帖"),
    REWARD_IN_FLIGHT_LIMIT("REWARD_IN_FLIGHT_LIMIT", "同时在途悬赏帖最多2条，请等待现有悬赏结束"),
    DISTRICT_INVALID("DISTRICT_INVALID", "仅支持成都行政区县"),
    EVENT_TIME_INVALID("EVENT_TIME_INVALID", "发生时间需在近30天内且不晚于当前时间"),
    REWARD_AMOUNT_INVALID("REWARD_AMOUNT_INVALID", "悬赏金额不在允许范围内"),
    REWARD_DAYS_INVALID("REWARD_DAYS_INVALID", "悬赏有效期仅支持7/15/30天"),
    AGREEMENT_REQUIRED("AGREEMENT_REQUIRED", "请先阅读并勾选协议"),
    BREED_FIELD_REQUIRED("BREED_FIELD_REQUIRED", "请完善相亲帖必填字段"),
    BREED_FORBIDDEN_WORD("BREED_FORBIDDEN_WORD", "内容包含交易类表述，请修改后重试"),
    EDIT_LIMIT("EDIT_LIMIT", "展示中的帖子仅允许编辑1次"),
    REWARD_FROZEN_NO_TAKEDOWN("REWARD_FROZEN_NO_TAKEDOWN", "悬赏托管中的帖子请先联系客服处理退款"),
    TOP_REJECTED("TOP_REJECTED", "置顶申请条件不满足"),
    TOP_SLOT_FULL("TOP_SLOT_FULL", "当前置顶位已满，请稍后购买"),
    TOP_DUPLICATE("TOP_DUPLICATE", "该帖子已有生效中的置顶"),
    REWARD_NOT_PAID("REWARD_NOT_PAID", "请先完成悬赏支付"),
    NOT_FOUND_CONFIRM_REQUIRED("NOT_FOUND_CONFIRM_REQUIRED", "请至少保留一条有效线索，或联系管理员处理");

    private final String code;
    private final String message;

    PostErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
