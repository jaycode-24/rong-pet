package com.jaycode.rongpet.module.experience;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.experience.dto.ExperienceRequests.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.jaycode.rongpet.security.StpAdminUtil;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name = "Admin-用户反馈")
@RestController @RequestMapping("/admin/v1/feedbacks") @RequiredArgsConstructor
public class AdminFeedbackController {
    private final ExperienceService service;
    private final AdminAuditService audit;
    @Operation(summary = "分页列表")
 @GetMapping public R<?> list(@RequestParam(defaultValue="1") int page){return R.ok(service.feedbacks(null,page));}
    @Operation(summary = "详情")
 @GetMapping("/{id}") public R<?> detail(@PathVariable long id){return R.ok(service.feedbackDetail(null,id));}
    @Operation(summary = "回复用户建议")
 @PostMapping("/{id}/replies") public R<?> reply(@PathVariable long id,@Valid @RequestBody Reply r){service.reply(StpAdminUtil.getLoginIdAsLong(),id,r.content().trim());audit.record("FEEDBACK_REPLY","FEEDBACK",id,"回复用户建议");return R.ok();}
}
