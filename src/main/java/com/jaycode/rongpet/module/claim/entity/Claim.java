package com.jaycode.rongpet.module.claim.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 招领认领申请，对应表 t_claim。
 * 先审后发：PENDING_AUDIT → APPROVED/REJECTED；拾宠人确认后 APPROVED → CONFIRMED，
 * 被拒绝的申请记为 REFUSED。申请内容与材料在确认前仅拾宠人与管理员可见。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_claim")
public class Claim extends BaseEntity {

    private Long postId;

    private Long userId;

    /** 主人特征、丢失经过，20-200 字 */
    private String description;

    /** 证明材料 JSON 数组，至少 2 项 */
    private String materials;

    /** PENDING_AUDIT/APPROVED/REJECTED/CONFIRMED/REFUSED */
    private String status;

    private String auditRemark;

    private LocalDateTime confirmTime;
}
