package com.jaycode.rongpet.module.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 宠物档案新增/编辑请求。基础必填：头像、名字、类型、品种、性别；其余为完善可选。
 */
@Data
public class PetRequest {

    @NotBlank(message = "宠物名字不能为空")
    @Size(max = 10, message = "宠物名字最长10字")
    private String name;

    @NotBlank(message = "宠物类型不能为空")
    private String petType;

    @NotBlank(message = "品种不能为空")
    @Size(max = 32, message = "品种最长32字")
    private String breed;

    @NotBlank(message = "性别不能为空")
    private String gender;

    @NotBlank(message = "宠物头像不能为空")
    private String avatarUrl;

    private LocalDate birthDate;

    private BigDecimal weight;

    private String sterilized;

    private String vaccineStatus;

    private String chipFeature;

    /** 最多 6 张 */
    private List<String> photos;

    private String remark;
}
