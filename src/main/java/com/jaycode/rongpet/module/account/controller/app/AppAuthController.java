package com.jaycode.rongpet.module.account.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.account.dto.LoginVO;
import com.jaycode.rongpet.module.account.dto.WxLoginRequest;
import com.jaycode.rongpet.module.account.service.AppAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端认证接口。
 */
@Tag(name = "App-认证", description = "小程序登录/登出")
@RestController
@RequestMapping("/app/v1/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;

    @Operation(summary = "微信登录", description = "wx.login 的 code 换取会话 token")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody WxLoginRequest request) {
        return R.ok(appAuthService.login(request));
    }

    @Operation(summary = "登出", description = "注销当前会话，使 token 失效")
    @PostMapping("/logout")
    public R<Void> logout() {
        appAuthService.logout();
        return R.ok();
    }
}
