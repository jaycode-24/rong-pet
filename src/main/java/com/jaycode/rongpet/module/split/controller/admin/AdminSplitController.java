package com.jaycode.rongpet.module.split.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.split.dto.SplitPlanVO;
import com.jaycode.rongpet.module.split.entity.SplitItem;
import com.jaycode.rongpet.module.split.service.SplitService;
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

import java.util.List;

/**
 * 后台评分分账：核验方案、按收款人汇总转付（资金操作仅超级管理员）。
 */
@Tag(name = "Admin-分账")
@RestController
@RequestMapping("/admin/v1/splits")
@RequiredArgsConstructor
public class AdminSplitController {

    private final SplitService splitService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "分账方案分页")
    @GetMapping
    public R<PageResult<SplitPlanVO>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(splitService.adminPage(status, pageNum, pageSize));
    }

    @Operation(summary = "分账明细")
    @GetMapping("/{planId}/items")
    public R<List<SplitItem>> items(@PathVariable long planId) {
        return R.ok(splitService.itemsOf(planId));
    }

    @Operation(summary = "核验方案")
    @PostMapping("/{planId}/approve")
    public R<Void> approve(@PathVariable long planId) {
        splitService.approve(planId, StpAdminUtil.getLoginIdAsLong());
        adminAuditService.record("SPLIT_APPROVE", "SPLIT_PLAN", planId, null);
        return R.ok();
    }

    @Operation(summary = "按收款人汇总转付", description = "仅超级管理员，单帖不超过5笔")
    @PostMapping("/{planId}/transfer")
    public R<Void> transfer(@PathVariable long planId,
                            @RequestBody(required = false) AdminActionRequest request) {
        StpAdminUtil.checkRole("SUPER");
        String voucherNo = request == null ? null : request.getVoucherNo();
        if (!StringUtils.hasText(voucherNo)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写转账凭证号");
        }
        long adminId = StpAdminUtil.getLoginIdAsLong();
        splitService.transfer(planId, adminId, "ADMIN-" + adminId, voucherNo);
        adminAuditService.record("SPLIT_TRANSFER", "SPLIT_PLAN", planId, "voucherNo=" + voucherNo);
        return R.ok();
    }
}
