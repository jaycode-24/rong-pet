package com.jaycode.rongpet.module.feeder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 喂养员认证申请，对应表 t_feeder_apply。退回后保留材料，可补交重新提交。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feeder_apply")
public class FeederApply extends BaseEntity {

    private Long userId;

    private String realNameEnc;

    private String idCardEnc;

    private String idCardHmac;

    private String phoneEnc;

    private String phoneHmac;

    private String district;

    /** 人脸/手持证件等材料 JSON 数组 */
    private String materials;

    /** PENDING/APPROVED/REJECTED */
    private String status;

    private String rejectReason;

    private Long auditAdminId;

    private LocalDateTime auditTime;
}
