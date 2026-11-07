package com.jaycode.rongpet.module.claim.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClaimVO {

    private Long id;

    private Long postId;

    private String postTitle;

    private Long userId;

    private String nickname;

    private String description;

    private List<String> materials;

    private String status;

    private String auditRemark;

    /** 确认后互见的脱敏联系方式 */
    private String contactMasked;

    private LocalDateTime confirmTime;

    private LocalDateTime createTime;
}
