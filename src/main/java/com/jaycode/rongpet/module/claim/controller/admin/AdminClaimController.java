package com.jaycode.rongpet.module.claim.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.claim.dto.ClaimVO;
import com.jaycode.rongpet.module.claim.service.ClaimService;
import com.jaycode.rongpet.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认领申请审核：先审后发，4 小时内完成。
 */
@Tag(name = "Admin-认领申请")
@RestController
@RequestMapping("/admin/v1/claims")
@RequiredArgsConstructor
public class AdminClaimController {

    private final ClaimService claimService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "认领申请分页")
    @GetMapping
    public R<PageResult<ClaimVO>> page(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long postId,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(claimService.adminPage(status, postId, pageNum, pageSize));
    }

    @Operation(summary = "审核认领申请")
    @PostMapping("/{claimId}/audit")
    public R<Void> audit(@PathVariable long claimId,
                         @RequestBody AdminActionRequest request) {
        boolean approve = Boolean.TRUE.equals(request.getApprove());
        claimService.audit(StpAdminUtil.getLoginIdAsLong(), claimId, approve, request.getReason());
        adminAuditService.record(approve ? "CLAIM_APPROVE" : "CLAIM_REJECT", "CLAIM", claimId,
                StringUtils.hasText(request.getReason()) ? request.getReason() : null);
        return R.ok();
    }
}
