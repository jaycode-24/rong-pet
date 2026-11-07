package com.jaycode.rongpet.module.clue.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum ClueErrorCode implements ErrorCode {

    CLUE_NOT_FOUND("CLUE_NOT_FOUND", "线索不存在"),
    CLUE_LIMIT("CLUE_LIMIT", "您对该帖子的线索提交次数已达上限"),
    CLUE_SELF("CLUE_SELF", "不能给自己的帖子提交线索"),
    CLUE_CHEAT("CLUE_CHEAT", "线索与发布者信息关联，已判定为无效"),
    CLUE_VALID_LIMIT("CLUE_VALID_LIMIT", "有效线索数量已达上限，请先取消部分有效标记"),
    CLUE_ONLY_PUBLISHER("CLUE_ONLY_PUBLISHER", "仅发布者可操作线索"),
    CLUE_SCORE_INVALID("CLUE_SCORE_INVALID", "评分必须为1-5星"),
    CLUE_SCORE_INCOMPLETE("CLUE_SCORE_INCOMPLETE", "请为全部有效线索评分");

    private final String code;
    private final String message;

    ClueErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
