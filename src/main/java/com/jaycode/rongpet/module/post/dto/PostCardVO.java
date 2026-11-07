package com.jaycode.rongpet.module.post.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostCardVO {

    private Long id;

    private String type;

    private String title;

    private String petType;

    private String breed;

    private String district;

    private LocalDateTime eventTime;

    /** 悬赏本金（分） */
    private Long rewardAmount;

    private String coverUrl;

    private String status;

    private LocalDateTime topExpireTime;

    private Integer clueCount;

    private String description;

    private LocalDateTime createTime;
}
