package com.jaycode.rongpet.module.feeder.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.feeder.dto.FeederApplyVO;
import com.jaycode.rongpet.module.feeder.dto.FeederCardVO;
import com.jaycode.rongpet.module.feeder.service.FeederService;
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
 * 后台喂养员管理：认证申请审核、资料列表与状态处置。
 */
@Tag(name = "Admin-喂养员")
@RestController
@RequestMapping("/admin/v1/feeders")
@RequiredArgsConstructor
public class AdminFeederController {

    private final FeederService feederService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "认证申请分页")
    @GetMapping("/applications")
    public R<PageResult<FeederApplyVO>> applications(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feederService.adminApplyPage(status, pageNum, pageSize));
    }

    @Operation(summary = "审核认证申请")
    @PostMapping("/applications/{applyId}/audit")
    public R<Void> auditApply(@PathVariable long applyId,
                              @RequestBody AdminActionRequest request) {
        boolean approve = Boolean.TRUE.equals(request.getApprove());
        if (!approve && !StringUtils.hasText(request.getReason())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写退回原因");
        }
        feederService.auditApply(StpAdminUtil.getLoginIdAsLong(), applyId, approve, request.getReason());
        adminAuditService.record(approve ? "FEEDER_APPROVE" : "FEEDER_REJECT", "FEEDER_APPLY", applyId,
                request.getReason());
        return R.ok();
    }

    @Operation(summary = "喂养员资料分页")
    @GetMapping("/profiles")
    public R<PageResult<FeederCardVO>> profiles(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feederService.adminProfilePage(status, pageNum, pageSize));
    }

    @Operation(summary = "喂养员状态处置", description = "action: PAUSE/BAN/RESUME")
    @PostMapping("/profiles/{profileId}/action")
    public R<Void> action(@PathVariable long profileId,
                          @RequestBody AdminActionRequest request) {
        feederService.adminAction(profileId, request.getAction(), request.getReason());
        adminAuditService.record("FEEDER_" + request.getAction(), "FEEDER_PROFILE", profileId,
                request.getReason());
        return R.ok();
    }
}
