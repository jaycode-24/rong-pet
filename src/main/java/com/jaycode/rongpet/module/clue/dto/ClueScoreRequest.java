package com.jaycode.rongpet.module.clue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 发布者对有效线索评分（1-5 星），提交后立即生成分账方案。
 */
@Data
public class ClueScoreRequest {

    @Valid
    @NotEmpty(message = "请提交评分")
    private List<Item> items;

    @Data
    public static class Item {
        private Long clueId;
        /** 1-5 */
        private Integer score;
        private String comment;
    }
}
