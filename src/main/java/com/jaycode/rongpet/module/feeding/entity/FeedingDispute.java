package com.jaycode.rongpet.module.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 喂养纠纷凭证记录，对应表 t_feeding_dispute。
 * 喂养为信息模式无资金，纠纷处理以双方凭证留痕、管理员归档为主（PRD FR-7.5）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feeding_dispute")
public class FeedingDispute extends BaseEntity {

    private Long feedingId;

    private Long submitterId;

    /** USER 用户 / FEEDER 喂养员 */
    private String side;

    private String reason;

    private String description;

    /** 凭证图片 JSON 数组 */
    private String evidenceImages;

    /** PENDING/PROCESSING/ARCHIVED */
    private String status;

    private String handleRemark;

    private Long handleAdminId;

    private LocalDateTime handleTime;
}
