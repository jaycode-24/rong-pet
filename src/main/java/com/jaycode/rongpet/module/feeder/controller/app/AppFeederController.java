package com.jaycode.rongpet.module.feeder.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.feeder.dto.FeederApplyRequest;
import com.jaycode.rongpet.module.feeder.dto.FeederApplyVO;
import com.jaycode.rongpet.module.feeder.dto.FeederCardVO;
import com.jaycode.rongpet.module.feeder.dto.FeederProfileRequest;
import com.jaycode.rongpet.module.feeder.service.FeederService;
import com.jaycode.rongpet.security.StpUserUtil;
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
 * 小程序喂养员：实名认证申请、三步资料完善、暂停/恢复接单。
 * 喂养员列表与详情为公开接口，见 /app/v1/public/feeders。
 */
@Tag(name = "App-喂养员")
@RestController
@RequestMapping("/app/v1/feeders")
@RequiredArgsConstructor
public class AppFeederController {

    private final FeederService feederService;

    @Operation(summary = "提交认证申请", description = "实名信息加密存储，材料至少1项")
    @PostMapping("/apply")
    public R<FeederApplyVO> apply(@Valid @RequestBody FeederApplyRequest request) {
        return R.ok(feederService.apply(StpUserUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "我的认证申请")
    @GetMapping("/apply/mine")
    public R<FeederApplyVO> myApply() {
        return R.ok(feederService.myApply(StpUserUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "我的喂养员资料")
    @GetMapping("/profile/mine")
    public R<FeederCardVO> myProfile() {
        return R.ok(feederService.myProfile(StpUserUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "完善资料", description = "介绍50-500字、可服务时段、参考价格；完成后对外展示")
    @PutMapping("/profile")
    public R<FeederCardVO> updateProfile(@RequestBody FeederProfileRequest request) {
        return R.ok(feederService.updateProfile(StpUserUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "暂停接单")
    @PostMapping("/profile/pause")
    public R<Void> pause() {
        feederService.pause(StpUserUtil.getLoginIdAsLong());
        return R.ok();
    }

    @Operation(summary = "恢复接单")
    @PostMapping("/profile/resume")
    public R<Void> resume() {
        feederService.resume(StpUserUtil.getLoginIdAsLong());
        return R.ok();
    }
}
