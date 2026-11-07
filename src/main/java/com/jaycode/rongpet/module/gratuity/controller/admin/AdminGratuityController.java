package com.jaycode.rongpet.module.gratuity.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.gratuity.dto.GratuityVO;
import com.jaycode.rongpet.module.gratuity.service.GratuityService;
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
 * 后台感谢金：核验转付与退款（资金操作仅超级管理员）。
 */
@Tag(name = "Admin-感谢金")
@RestController
@RequestMapping("/admin/v1/gratuity")
@RequiredArgsConstructor
public class AdminGratuityController {

    private final GratuityService gratuityService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "感谢金分页")
    @GetMapping
    public R<PageResult<GratuityVO>> page(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long postId,
                                          @RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(gratuityService.adminPage(status, postId, pageNum, pageSize));
    }

    @Operation(summary = "核验转付", description = "仅超级管理员，需转账凭证号")
    @PostMapping("/{gratuityId}/transfer")
    public R<Void> transfer(@PathVariable long gratuityId,
                            @RequestBody(required = false) AdminActionRequest request) {
        StpAdminUtil.checkRole("SUPER");
        String voucherNo = request == null ? null : request.getVoucherNo();
        if (!StringUtils.hasText(voucherNo)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写转账凭证号");
        }
        long adminId = StpAdminUtil.getLoginIdAsLong();
        gratuityService.transfer(gratuityId, adminId, "ADMIN-" + adminId, voucherNo);
        adminAuditService.record("GRATUITY_TRANSFER", "GRATUITY", gratuityId, "voucherNo=" + voucherNo);
        return R.ok();
    }

    @Operation(summary = "感谢金退款", description = "仅超级管理员，原路退回")
    @PostMapping("/{gratuityId}/refund")
    public R<Void> refund(@PathVariable long gratuityId,
                          @RequestBody(required = false) AdminActionRequest request) {
        StpAdminUtil.checkRole("SUPER");
        String reason = request == null ? null : request.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写退款原因");
        }
        gratuityService.refund(gratuityId, StpAdminUtil.getLoginIdAsLong(), reason);
        adminAuditService.record("GRATUITY_REFUND", "GRATUITY", gratuityId, reason);
        return R.ok();
    }
}
