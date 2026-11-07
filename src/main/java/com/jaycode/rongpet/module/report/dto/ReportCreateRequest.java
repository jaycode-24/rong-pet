package com.jaycode.rongpet.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户举报请求。targetType 支持 POST/CLUE/USER/FEEDING。
 */
@Data
public class ReportCreateRequest {

    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;

    @NotNull(message = "举报对象ID不能为空")
    private Long targetId;

    @NotBlank(message = "请选择举报原因")
    @Size(max = 64, message = "举报原因最长64字")
    private String reason;

    @Size(max = 500, message = "补充说明最长500字")
    private String description;
}
