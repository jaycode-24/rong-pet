package com.jaycode.rongpet.module.account.controller.admin;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.account.dto.AdminLoginRequest;
import com.jaycode.rongpet.module.account.dto.AdminLoginVO;
import com.jaycode.rongpet.module.account.dto.AdminUserVO;
import com.jaycode.rongpet.module.account.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认证接口。登录入口豁免，其余 /admin/v1/** 由拦截器强制校验会话。
 */
@Tag(name = "Admin-认证", description = "运营后台登录/登出")
@RestController
@RequestMapping("/admin/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "账号密码登录", description = "连续5次密码错误锁定30分钟")
    @PostMapping("/login")
    public R<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest servletRequest) {
        return R.ok(adminAuthService.login(request, servletRequest));
    }

    @Operation(summary = "当前管理员", description = "密码等敏感字段不返回")
    @GetMapping("/me")
    public R<AdminUserVO> me() {
        return R.ok(adminAuthService.me());
    }

    @Operation(summary = "登出", description = "注销当前会话，使 token 失效")
    @PostMapping("/logout")
    public R<Void> logout() {
        adminAuthService.logout();
        return R.ok();
    }
}
