package com.jaycode.rongpet.module.experience.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class ExperienceRequests {
    private ExperienceRequests() {}
    public record Feedback(@NotBlank @Pattern(regexp="功能建议|使用问题|内容与服务|其他意见") String category, @NotBlank @Size(min=5,max=1000) String content) {}
    public record Scene(@NotBlank String scene) {}
    public record Text(@NotBlank @Size(max=1000) String content) {}
    public record Reply(@NotBlank @Size(max=2000) String content) {}
    public record Generate(@NotBlank String style, @NotBlank @Size(max=64) String requestKey) {}
    public record Evidence(@NotBlank @Size(min=10,max=2000) String content,
            @Schema(description="证据地址数组，不接受JSON字符串或嵌套数组") @NotEmpty @Size(max=9) List<@NotBlank @Size(max=1024) String> evidence) {}
    public record Found(@NotEmpty @Size(max=5) List<@NotNull Long> clueIds, @NotBlank @Size(min=10,max=500) String reason) {}
    public record Decision(@NotBlank @Pattern(regexp="REJECTED|UPHELD|REFUND_APPROVED") String decision, @NotBlank @Size(min=5,max=2000) String reason) {}
}
