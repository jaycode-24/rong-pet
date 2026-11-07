package com.jaycode.rongpet.module.admin.controller;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.entity.AdminAuditLog;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台审计日志查询（append-only，仅超级管理员可读）。
 */
@Tag(name = "Admin-审计日志")
@RestController
@RequestMapping("/admin/v1/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditService adminAuditService;

    @Operation(summary = "审计日志分页")
    @GetMapping
    public R<PageResult<AdminAuditLog>> page(@RequestParam(required = false) Long adminId,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        StpAdminUtil.checkRole("SUPER");
        return R.ok(adminAuditService.page(adminId, action, pageNum, pageSize));
    }
}
