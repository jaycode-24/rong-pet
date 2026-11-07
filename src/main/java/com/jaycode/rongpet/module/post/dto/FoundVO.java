package com.jaycode.rongpet.module.post.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已找回列表卡片：仅公示汇总分账口径，不泄露明细。
 */
@Data
public class FoundVO {

    private Long id;

    private String type;

    private String title;

    private String petType;

    private String breed;

    private String district;

    private LocalDateTime eventTime;

    private LocalDateTime foundTime;

    private long findDays;

    private Long rewardAmount;

    private String coverUrl;

    /** 受分账的线索提供者人数 */
    private int participantCount;

    /** 分配总额（分） */
    private long totalAmount;
}
