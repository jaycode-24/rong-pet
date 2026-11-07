package com.jaycode.rongpet.module.post.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 免费置顶申请：上传付款/权益截图，由运营人工核验。
 */
@Data
public class TopApplyRequest {

    @NotBlank(message = "请上传置顶凭证截图")
    private String screenshotUrl;
}
