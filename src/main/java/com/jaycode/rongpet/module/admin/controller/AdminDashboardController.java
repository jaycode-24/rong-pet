package com.jaycode.rongpet.module.admin.controller;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.dto.DashboardVO;
import com.jaycode.rongpet.module.admin.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台数据看板。
 */
@Tag(name = "Admin-数据看板")
@RestController
@RequestMapping("/admin/v1/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "核心指标总览")
    @GetMapping("/overview")
    public R<DashboardVO> overview() {
        return R.ok(dashboardService.overview());
    }
}
