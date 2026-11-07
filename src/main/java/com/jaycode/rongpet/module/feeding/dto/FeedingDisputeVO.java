package com.jaycode.rongpet.module.feeding.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 喂养纠纷视图。双方互相可见对方提交的凭证与管理员处理结论。
 */
@Data
public class FeedingDisputeVO {

    private Long id;

    private Long feedingId;

    private Long submitterId;

    private String submitterNickname;

    /** USER 用户 / FEEDER 喂养员 */
    private String side;

    private String reason;

    private String description;

    private List<String> evidenceImages;

    /** PENDING/PROCESSING/ARCHIVED */
    private String status;

    private String handleRemark;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
