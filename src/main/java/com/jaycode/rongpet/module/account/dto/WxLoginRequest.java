package com.jaycode.rongpet.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序微信登录请求。code 为 wx.login() 临时凭证，禁止携带任何用户敏感信息。
 */
@Data
public class WxLoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;
}
