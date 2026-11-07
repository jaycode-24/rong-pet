package com.jaycode.rongpet.module.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 举报记录，对应表 t_report。处理以记录留痕为主。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_report")
public class Report extends BaseEntity {

    private Long reporterId;

    private String targetType;

    private Long targetId;

    private String reason;

    private String description;

    /** PENDING/HANDLED/INVALID */
    private String status;

    private String handleRemark;

    private Long handleAdminId;

    private LocalDateTime handleTime;
}
