package com.jaycode.rongpet.module.feeder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 喂养员资料，对应表 t_feeder_profile。实名信息仅存密文 + HMAC 索引。
 * 三步任务（介绍 / 时段 / 价格）完成后 profileComplete=1 才对外展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feeder_profile")
public class FeederProfile extends BaseEntity {

    private Long userId;

    private String realNameEnc;

    private String idCardEnc;

    private String idCardHmac;

    private String phoneEnc;

    private String phoneHmac;

    private String district;

    /** 可服务区县 JSON 数组 */
    private String serviceDistricts;

    private String serviceIntro;

    /** 可服务时段 JSON（按周） */
    private String serviceTimes;

    private Long priceOnce;

    private Long priceDaily;

    private Integer depositAccepted;

    /** ACTIVE/PAUSED/EXPIRED/BANNED */
    private String status;

    private Integer profileComplete;

    private LocalDateTime certExpireTime;

    private Integer connectCount;

    private LocalDateTime lastActiveTime;
}
