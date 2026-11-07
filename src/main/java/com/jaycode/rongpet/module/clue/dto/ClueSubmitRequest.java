package com.jaycode.rongpet.module.clue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClueSubmitRequest {

    @NotBlank(message = "线索描述不能为空")
    @Size(max = 500, message = "线索描述最长500字")
    private String content;

    private List<String> images;

    @NotBlank(message = "联系方式不能为空")
    private String contactPhone;
}
