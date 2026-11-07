package com.jaycode.rongpet.module.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 置顶记录，对应表 t_top_record。付费/免费/人工三类共用前 3 个展示位。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_top_record")
public class TopRecord extends BaseEntity {

    private Long postId;

    /** PAID/FREE/MANUAL */
    private String type;

    private Integer days;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String orderNo;

    private String screenshotUrl;

    private String reason;

    /** PENDING/ACTIVE/REJECTED/EXPIRED */
    private String status;
}
