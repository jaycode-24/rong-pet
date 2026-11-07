package com.jaycode.rongpet.module.admin.controller;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.dto.ConfigUpdateRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.config.entity.SysConfig;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营配置：读取对所有管理员开放，修改仅超级管理员。
 */
@Tag(name = "Admin-运营配置")
@RestController
@RequestMapping("/admin/v1/configs")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigService sysConfigService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "全部配置")
    @GetMapping
    public R<List<SysConfig>> list() {
        return R.ok(sysConfigService.listAll());
    }

    @Operation(summary = "更新配置", description = "仅超级管理员，修改后最迟 60 秒生效")
    @PutMapping
    public R<Void> update(@Valid @RequestBody ConfigUpdateRequest request) {
        StpAdminUtil.checkRole("SUPER");
        sysConfigService.update(request.getKey(), request.getValue(), request.getRemark());
        adminAuditService.record("CONFIG_UPDATE", "CONFIG", null,
                request.getKey() + "=" + request.getValue());
        return R.ok();
    }
}
