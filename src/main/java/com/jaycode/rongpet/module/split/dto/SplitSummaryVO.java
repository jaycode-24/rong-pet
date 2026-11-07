package com.jaycode.rongpet.module.split.dto;

import lombok.Data;

import java.util.List;

/**
 * 分账汇总：对外仅公示汇总口径，明细按当事人可见性裁剪。
 */
@Data
public class SplitSummaryVO {

    /** 受分账的线索提供者人数 */
    private int participantCount;

    /** 分配总额（分） */
    private long totalAmount;

    /** 明细列表（可能为空或部分可见） */
    private List<Item> items;

    @Data
    public static class Item {
        private Long clueId;
        private String nickname;
        private Integer score;
        private Long amount;
        private boolean mine;
    }
}
