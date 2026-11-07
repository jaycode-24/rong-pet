package com.jaycode.rongpet.module.experience;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.experience.dto.ExperienceRequests.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.jaycode.rongpet.security.*;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name = "App/Admin-悬赏争议")
@RestController @RequiredArgsConstructor
public class RewardDisputeController {
 private final RewardDisputeService service;
 private final AdminAuditService audit;
 @Operation(summary = "提交找回争议，证据使用数组")
 @PostMapping("/app/v1/posts/{id}/reward-disputes") public R<?> submit(@PathVariable long id,@Valid @RequestBody Evidence r){service.submit(StpUserUtil.getLoginIdAsLong(),id,r.content(),r.evidence());return R.ok();}
 @Operation(summary = "人工核验找回并选定有效线索")
 @PostMapping("/admin/v1/reward-disputes/{id}/confirm-found") public R<?> confirmFound(@PathVariable long id,@Valid @RequestBody Found r){StpAdminUtil.checkRole("SUPER");service.confirmFound(StpAdminUtil.getLoginIdAsLong(),id,r.clueIds(),r.reason());audit.record("DISPUTE_CONFIRM_FOUND","REWARD_DISPUTE",id,r.reason());return R.ok();}
 @Operation(summary = "人工核验未找回退款")
 @PostMapping("/admin/v1/posts/{id}/refund-review") public R<?> approveRefund(@PathVariable long id,@Valid @RequestBody Decision r){StpAdminUtil.checkRole("SUPER");service.approveRefund(StpAdminUtil.getLoginIdAsLong(),id,r.reason());audit.record("REWARD_REFUND_REVIEW","POST",id,r.reason());return R.ok();}
 @Operation(summary = "本人争议列表")
 @GetMapping("/app/v1/reward-disputes") public R<?> mine(@RequestParam(defaultValue="1") int page){return R.ok(service.list(StpUserUtil.getLoginIdAsLong(),page));}
 @Operation(summary = "分页列表")
 @GetMapping("/admin/v1/reward-disputes") public R<?> list(@RequestParam(defaultValue="1") int page){return R.ok(service.list(null,page));}
 @Operation(summary = "处理争议并通知双方")
 @PostMapping("/admin/v1/reward-disputes/{id}/decision") public R<?> resolve(@PathVariable long id,@Valid @RequestBody Decision r){StpAdminUtil.checkRole("SUPER");service.resolve(StpAdminUtil.getLoginIdAsLong(),id,r.decision(),r.reason());audit.record("REWARD_DISPUTE_DECISION","REWARD_DISPUTE",id,r.reason());return R.ok();}
}
