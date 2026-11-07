package com.jaycode.rongpet.module.account.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.account.dto.UpdateProfileRequest;
import com.jaycode.rongpet.module.account.dto.UserVO;
import com.jaycode.rongpet.module.account.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端个人资料接口。仅限当前登录用户操作自身。
 */
@Tag(name = "App-用户", description = "小程序个人资料")
@RestController
@RequestMapping("/app/v1/user")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

    @Operation(summary = "我的资料", description = "手机号等敏感字段脱敏返回")
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(appUserService.me());
    }

    @Operation(summary = "更新资料", description = "按字段跳过空值更新昵称/头像")
    @PutMapping("/me")
    public R<UserVO> updateMe(@RequestBody UpdateProfileRequest request) {
        return R.ok(appUserService.updateProfile(request));
    }

    @Operation(summary = "大额悬赏实名补充", description = "姓名与身份证加密存储，仅管理员可按权限查看脱敏信息")
    @PostMapping("/real-name")
    public R<Void> submitRealName(@Valid @RequestBody com.jaycode.rongpet.module.account.dto.RealNameRequest request) {
        appUserService.submitRealName(request);
        return R.ok();
    }

    @Operation(summary = "注销账号", description = "存在在途资金/订单/喂养请求/进行中帖子时拒绝；注销后不可登录并脱敏个人资料")
    @PostMapping("/cancel")
    public R<Void> cancel() {
        appUserService.cancel();
        return R.ok();
    }
}
