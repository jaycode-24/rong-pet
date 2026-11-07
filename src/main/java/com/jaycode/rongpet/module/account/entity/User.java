package com.jaycode.rongpet.module.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 小程序用户，对应表 t_user。
 * 手机号/实名/身份证仅存密文列 + HMAC 索引列，展示层一律脱敏；
 * open_id 为账号主体，union_id 预留多端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    private String openId;

    private String unionId;

    private String nickname;

    private String avatarUrl;

    /** 手机号密文（AES-GCM） */
    private String phoneEnc;

    /** 手机号 HMAC 索引 */
    private String phoneHmac;

    /** NORMAL/LIMIT_POST/LIMIT_TRADE/BANNED/FROZEN */
    private String status;

    /** 实名姓名密文（大额悬赏/喂养员认证） */
    private String realNameEnc;

    /** 身份证号密文 */
    private String idCardEnc;

    /** 身份证号 HMAC 索引 */
    private String idCardHmac;

    /** 所属城市，V1.0 仅成都 */
    private String city;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    /** 注销即冻结 */
    private LocalDateTime cancelTime;
}
