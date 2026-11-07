package com.jaycode.rongpet.module.report.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.report.entity.Report;
import com.jaycode.rongpet.module.report.service.ReportService;
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
 * 后台举报处理：24 小时内处理，结果留痕。
 */
@Tag(name = "Admin-举报")
@RestController
@RequestMapping("/admin/v1/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "举报分页")
    @GetMapping
    public R<PageResult<Report>> page(@RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(reportService.page(status, pageNum, pageSize));
    }

    @Operation(summary = "处理举报", description = "operation: HANDLED 成立 / INVALID 不成立")
    @PostMapping("/{reportId}/handle")
    public R<Void> handle(@PathVariable long reportId,
                          @RequestBody AdminActionRequest request) {
        String status = StringUtils.hasText(request.getOperation()) ? request.getOperation() : "HANDLED";
        if (!"HANDLED".equals(status) && !"INVALID".equals(status)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "处理结论仅支持 HANDLED/INVALID");
        }
        reportService.handle(reportId, StpAdminUtil.getLoginIdAsLong(), status, request.getReason());
        adminAuditService.record("REPORT_" + status, "REPORT", reportId, request.getReason());
        return R.ok();
    }
}
