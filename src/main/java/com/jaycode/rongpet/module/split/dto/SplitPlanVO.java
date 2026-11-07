package com.jaycode.rongpet.module.split.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台分账方案视图。
 */
@Data
public class SplitPlanVO {

    private Long id;

    private Long postId;

    private String postTitle;

    private Long totalAmount;

    private String status;

    private boolean reviewRequired;

    private LocalDateTime createTime;

    private List<SplitSummaryVO.Item> items;
}
