package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 悬赏找回争议，对应表 t_reward_dispute。
 * 线索提供者认为宠物已找回但发帖人未确认时发起，人工核验期间暂停悬赏到期退款。
 * 库内另有生成列 pending_guard 配合唯一键，保证同一帖同一用户至多一条未结（PENDING/UPHELD）争议。
 * 状态：PENDING 待核验 → UPHELD 支持（核验已找回，进入评分分账）/ REJECTED 驳回 /
 * REFUND_APPROVED 批准按未找回退款。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reward_dispute")
public class RewardDispute extends BaseEntity {

    /** 关联悬赏帖 ID */
    private Long postId;

    /** 发起争议的线索提供者用户 ID */
    private Long userId;

    /** 争议理由说明 */
    private String content;

    /** 凭证图片 JSON 数组，经 EvidenceCodec 编码存储 */
    private String evidence;

    /** PENDING/UPHELD/REJECTED/REFUND_APPROVED */
    private String status;

    /** 管理员处理结论说明 */
    private String decision;

    /** 处理争议的管理员 ID，用户发起时为空 */
    private Long adminId;

}
