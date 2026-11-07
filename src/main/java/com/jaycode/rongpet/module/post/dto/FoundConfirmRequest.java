package com.jaycode.rongpet.module.post.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 确认找回请求：上传归还凭证，帖子转待评分。
 */
@Data
public class FoundConfirmRequest {

    @NotEmpty(message = "请上传归还凭证")
    private List<String> proofImages;

    @Size(max = 500, message = "找回经过最长500字")
    private String foundNote;
}
