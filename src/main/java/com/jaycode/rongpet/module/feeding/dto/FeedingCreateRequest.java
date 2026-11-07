package com.jaycode.rongpet.module.feeding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交喂养请求。平台仅提供信息对接，无支付环节。
 */
@Data
public class FeedingCreateRequest {

    /** 定向喂养员用户ID；为空表示发布公开请求 */
    private Long feederId;

    @NotNull(message = "请选择宠物档案")
    private Long petId;

    @NotBlank(message = "请选择服务类型")
    private String serviceType;

    @NotNull(message = "请选择服务开始时间")
    private LocalDateTime startTime;

    /** 按日上门时必填 */
    private LocalDateTime endTime;

    private String dailyTime;

    @NotBlank(message = "请选择区县")
    private String district;

    @NotBlank(message = "请填写服务地址")
    @Size(max = 200, message = "地址最长200字")
    private String address;

    @NotBlank(message = "请填写联系人姓名")
    @Size(max = 32, message = "联系人姓名最长32字")
    private String contactName;

    @NotBlank(message = "请填写联系电话")
    private String contactPhone;

    @Size(max = 200, message = "宠物情况最长200字")
    private String petNote;

    @Size(max = 200, message = "特殊要求最长200字")
    private String specialRequire;

    /** 必须勾选《上门喂养服务须知》 */
    private Boolean agreed;
}
