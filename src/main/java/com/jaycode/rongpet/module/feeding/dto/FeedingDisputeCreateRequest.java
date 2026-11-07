package com.jaycode.rongpet.module.feeding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 喂养纠纷提交请求（PRD FR-7.5）。仅喂养请求双方可提交，凭证图片 0-9 张。
 */
@Data
public class FeedingDisputeCreateRequest {

    @NotNull(message = "喂养请求ID不能为空")
    private Long feedingId;

    @NotBlank(message = "请选择纠纷原因")
    @Size(max = 64, message = "纠纷原因最长64字")
    private String reason;

    @NotBlank(message = "请填写情况描述")
    @Size(min = 10, max = 500, message = "情况描述需在10-500字之间")
    private String description;

    /** 凭证图片 key/URL 列表，最多 9 张 */
    @Size(max = 9, message = "凭证图片最多9张")
    private List<String> evidenceImages;
}
