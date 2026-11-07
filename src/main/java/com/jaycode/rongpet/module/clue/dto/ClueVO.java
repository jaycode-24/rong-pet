package com.jaycode.rongpet.module.clue.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClueVO {

    private Long id;

    private Long postId;

    private Long userId;

    private String nickname;

    private String content;

    private List<String> images;

    private String contactMasked;

    private String status;

    private String invalidReason;

    private Integer score;

    private String scoreComment;

    private String scoreSource;

    private Long shareAmount;

    private String payStatus;

    private LocalDateTime createTime;
}
