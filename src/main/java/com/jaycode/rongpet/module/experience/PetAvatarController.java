package com.jaycode.rongpet.module.experience;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.experience.dto.ExperienceRequests.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.jaycode.rongpet.security.StpUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name = "App-AI宠物形象")
@RestController @RequestMapping("/app/v1/pets/{petId}/avatars") @RequiredArgsConstructor
public class PetAvatarController {
 private final PetAvatarService service;
 @Operation(summary = "AI生成额度和价格")
 @GetMapping("/quota") public R<?> quota(@PathVariable long petId){return R.ok(service.quota(StpUserUtil.getLoginIdAsLong(),petId));}
 @Operation(summary = "创建申请")
 @PostMapping public R<?> create(@PathVariable long petId,@Valid @RequestBody Generate r){return R.ok(service.create(StpUserUtil.getLoginIdAsLong(),petId,r.style(),r.requestKey()));}
 @Operation(summary = "分页列表")
 @GetMapping public R<?> list(@PathVariable long petId){return R.ok(service.list(StpUserUtil.getLoginIdAsLong(),petId));}
 @Operation(summary = "生成任务状态")
 @GetMapping("/{id}") public R<?> task(@PathVariable long petId,@PathVariable long id){return R.ok(service.task(StpUserUtil.getLoginIdAsLong(),id));}
 @Operation(summary = "付款，同一任务最多重建一次支付单")
 @PostMapping("/{id}/pay") public R<?> pay(@PathVariable long petId,@PathVariable long id){return R.ok(service.pay(StpUserUtil.getLoginIdAsLong(),id));}
 @Operation(summary = "选用生成结果")
 @PostMapping("/{id}/select") public R<?> select(@PathVariable long petId,@PathVariable long id){service.select(StpUserUtil.getLoginIdAsLong(),petId,id);return R.ok();}
}
