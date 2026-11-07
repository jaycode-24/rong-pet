package com.jaycode.rongpet.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运营配置更新请求（仅超级管理员）。
 */
@Data
public class ConfigUpdateRequest {

    @NotBlank(message = "配置键不能为空")
    private String key;

    @NotBlank(message = "配置值不能为空")
    private String value;

    private String remark;
}
