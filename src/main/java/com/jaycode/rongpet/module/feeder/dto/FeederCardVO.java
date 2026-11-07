package com.jaycode.rongpet.module.feeder.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FeederCardVO {

    private Long id;

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private String district;

    private List<String> serviceDistricts;

    private String serviceIntro;

    private List<Map<String, Object>> serviceTimes;

    private Long priceOnce;

    private Long priceDaily;

    private boolean depositAccepted;

    private Integer connectCount;

    private boolean certified;

    /** ACTIVE/PAUSED/EXPIRED/BANNED */
    private String status;

    private boolean profileComplete;

    private LocalDateTime certExpireTime;

    private LocalDateTime lastActiveTime;
}
