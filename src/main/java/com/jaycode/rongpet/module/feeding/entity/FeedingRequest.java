package com.jaycode.rongpet.module.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 喂养请求，对应表 t_feeding_request。纯信息模式，无任何支付字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feeding_request")
public class FeedingRequest extends BaseEntity {

    private Long userId;

    /** 定向喂养员用户ID，公开请求为空 */
    private Long feederId;

    private Long petId;

    /** ONCE 单次 / MULTI_DAY 按日 */
    private String serviceType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String dailyTime;

    private String district;

    private String addressEnc;

    private String contactName;

    private String contactEnc;

    private String contactHmac;

    private String petNote;

    private String specialRequire;

    /** PENDING/CONTACTED/COMPLETED/CLOSED */
    private String status;

    private String closeReason;

    private LocalDateTime contactTime;

    private LocalDateTime completeTime;
}
