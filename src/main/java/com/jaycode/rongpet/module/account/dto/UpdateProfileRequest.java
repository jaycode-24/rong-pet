package com.jaycode.rongpet.module.account.dto;

import lombok.Data;

/**
 * 更新个人资料请求。昵称需过内容安全审核后再落库（M1 阶段先透传）。
 */
@Data
public class UpdateProfileRequest {

    private String nickname;

    private String avatarUrl;
}
