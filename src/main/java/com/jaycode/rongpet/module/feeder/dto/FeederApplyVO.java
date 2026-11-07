package com.jaycode.rongpet.module.feeder.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeederApplyVO {

    private Long id;

    private Long userId;

    private String nickname;

    /** 脱敏姓名 */
    private String realNameMasked;

    /** 脱敏身份证 */
    private String idCardMasked;

    /** 脱敏手机号 */
    private String phoneMasked;

    private String district;

    private java.util.List<String> materials;

    private String status;

    private String rejectReason;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;
}
