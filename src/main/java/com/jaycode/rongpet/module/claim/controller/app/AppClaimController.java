package com.jaycode.rongpet.module.claim.controller.app;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.dto.ReasonRequest;
import com.jaycode.rongpet.module.claim.dto.ClaimSubmitRequest;
import com.jaycode.rongpet.module.claim.dto.ClaimVO;
import com.jaycode.rongpet.module.claim.service.ClaimService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序认领：申请人提交、拾宠人确认/拒绝、确认归还。
 */
@Tag(name = "App-认领")
@RestController
@RequestMapping("/app/v1")
@RequiredArgsConstructor
public class AppClaimController {

    private final ClaimService claimService;

    @Operation(summary = "提交认领申请", description = "描述20-200字，证明材料至少2项，先审后发")
    @PostMapping("/posts/{postId}/claims")
    public R<ClaimVO> submit(@PathVariable long postId,
                             @Valid @RequestBody ClaimSubmitRequest request) {
        return R.ok(claimService.submit(StpUserUtil.getLoginIdAsLong(), postId, request));
    }

    @Operation(summary = "帖子下的认领申请", description = "拾宠人看已通过审核的申请，申请人仅看自己的")
    @GetMapping("/posts/{postId}/claims")
    public R<List<ClaimVO>> list(@PathVariable long postId) {
        return R.ok(claimService.listForPost(StpUserUtil.getLoginIdAsLong(), postId));
    }

    @Operation(summary = "我的认领申请")
    @GetMapping("/claims/mine")
    public R<PageResult<ClaimVO>> mine(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(claimService.mine(StpUserUtil.getLoginIdAsLong(), pageNum, pageSize));
    }

    @Operation(summary = "拾宠人确认认领")
    @PostMapping("/claims/{claimId}/confirm")
    public R<Void> confirm(@PathVariable long claimId) {
        claimService.confirm(StpUserUtil.getLoginIdAsLong(), claimId);
        return R.ok();
    }

    @Operation(summary = "拾宠人拒绝认领")
    @PostMapping("/claims/{claimId}/refuse")
    public R<Void> refuse(@PathVariable long claimId,
                          @RequestBody(required = false) ReasonRequest request) {
        claimService.refuse(StpUserUtil.getLoginIdAsLong(), claimId,
                request == null ? null : request.getReason());
        return R.ok();
    }

    @Operation(summary = "确认归还", description = "需已确认认领申请，完成后进入已找回列表")
    @PostMapping("/posts/{postId}/confirm-return")
    public R<Void> confirmReturn(@PathVariable long postId) {
        claimService.confirmReturn(StpUserUtil.getLoginIdAsLong(), postId);
        return R.ok();
    }
}
