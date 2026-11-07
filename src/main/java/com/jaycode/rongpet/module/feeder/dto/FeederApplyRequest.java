package com.jaycode.rongpet.module.feeder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 喂养员认证申请：真实姓名、身份证号、联系电话、服务区县、人脸/证件材料。
 */
@Data
public class FeederApplyRequest {

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 32, message = "姓名最长32字")
    private String realName;

    @NotBlank(message = "请填写身份证号")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;

    @NotBlank(message = "请填写联系电话")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请选择服务区县")
    private String district;

    /** 人脸核验 / 手持证件等材料，至少 1 项 */
    private List<String> materials;
}
