package com.jaycode.rongpet.module.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交认领申请：描述 20-200 字，证明材料至少 2 项。
 */
@Data
public class ClaimSubmitRequest {

    @NotBlank(message = "请填写认领描述")
    @Size(min = 20, max = 200, message = "认领描述需20-200字")
    private String description;

    @Size(min = 2, message = "请至少上传2项证明材料")
    private List<String> materials;
}
