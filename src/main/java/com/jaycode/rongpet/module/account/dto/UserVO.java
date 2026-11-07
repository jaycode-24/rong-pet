package com.jaycode.rongpet.module.account.dto;

import lombok.Data;

/**
 * 用户信息视图。敏感字段一律脱敏输出，密文列绝不出现在响应体中。
 */
@Data
public class UserVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    /** 展示态：138****1234 */
    private String maskedPhone;

    /** NORMAL/LIMIT_POST/LIMIT_TRADE/BANNED/FROZEN */
    private String status;

    /** 是否已完成实名补充（大额悬赏/喂养员认证） */
    private Boolean realNameVerified;
}
