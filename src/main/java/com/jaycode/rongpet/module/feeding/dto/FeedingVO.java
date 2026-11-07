package com.jaycode.rongpet.module.feeding.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedingVO {

    private Long id;

    private Long userId;

    private String ownerNickname;

    private Long feederId;

    private String feederNickname;

    private Long petId;

    private String petName;

    private String serviceType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String dailyTime;

    private String district;

    /** 对接成功前隐藏，避免住址泄露 */
    private String address;

    private String contactName;

    /** 脱敏联系方式 */
    private String contactMasked;

    private String petNote;

    private String specialRequire;

    private String status;

    private String closeReason;

    private LocalDateTime contactTime;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;

    /** 免责提示恒定返回，前端显著展示 */
    private String disclaimer;
}
