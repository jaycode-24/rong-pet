package com.jaycode.rongpet.module.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帖子（寻宠/招领/相亲），对应表 t_post。
 * 金额均为分；宠物字段在发布时固化为快照，档案后续修改不影响历史帖子。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_post")
public class Post extends BaseEntity {

    private String type;

    private Long userId;

    private Long petId;

    private String petSnapshot;

    private String petType;

    private String breed;

    private String title;

    private String gender;

    private String color;

    private String size;

    private String sterilized;

    private String chipFeature;

    private BigDecimal age;

    private BigDecimal weight;

    private String vaccineStatus;

    private String dewormStatus;

    private String geneticScreening;

    private String breedIntent;

    private String expectCondition;

    private String district;

    private String location;

    private String locationLatLng;

    private LocalDateTime eventTime;

    private String contactEnc;

    private String contactHmac;

    private String description;

    private String images;

    private String coverUrl;

    private Long rewardAmount;

    private Long rewardFee;

    private String rewardStatus;

    private Integer rewardDays;

    private LocalDateTime rewardExpireTime;

    private LocalDateTime refundNoticeTime;

    private Long gratuityAmount;

    private String status;

    private String rejectReason;

    private String auditRemark;

    private Integer editCount;

    private String topType;

    private LocalDateTime topExpireTime;

    private LocalDateTime expireTime;

    private Integer extendCount;

    private LocalDateTime foundTime;

    private String foundNote;

    private String foundProof;

    private Integer viewCount;

    private Integer clueCount;

    private Integer claimCount;

    private String agreementVersion;
}
