package com.jaycode.rongpet.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 大额悬赏前的实名补充。仅存密文与 HMAC 索引，后台默认脱敏展示。
 */
@Data
public class RealNameRequest {

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 32, message = "姓名最长32字")
    private String realName;

    @NotBlank(message = "请填写身份证号")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;
}
