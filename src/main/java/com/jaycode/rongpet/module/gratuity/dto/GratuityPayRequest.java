package com.jaycode.rongpet.module.gratuity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发放感谢金请求（失主支付，金额单位分）。
 */
@Data
public class GratuityPayRequest {

    @NotNull(message = "感谢金金额不能为空")
    private Long amount;

    @NotBlank(message = "收款人微信号或手机号不能为空")
    private String payeeAccount;
}
