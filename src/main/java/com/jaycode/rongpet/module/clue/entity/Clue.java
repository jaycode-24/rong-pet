package com.jaycode.rongpet.module.clue.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 线索，对应表 t_clue。联系方式密文存储，仅发布者与管理员可见（脱敏展示）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_clue")
public class Clue extends BaseEntity {

    private Long postId;

    private Long userId;

    private String content;

    private String images;

    private String contactEnc;

    private String contactHmac;

    /** PENDING/VALID/INVALID/CHEATED */
    private String status;

    private String invalidReason;

    private Integer score;

    private String scoreComment;

    /** PUBLISHER/SYSTEM_DEFAULT */
    private String scoreSource;

    /** 分得金额（分） */
    private Long shareAmount;

    /** UNPAID/PAID/FROZEN/FAILED */
    private String payStatus;

    private Integer detailPublic;

    /** 48 小时未处理提醒时间，仅提醒一次 */
    private LocalDateTime remindTime;
}
