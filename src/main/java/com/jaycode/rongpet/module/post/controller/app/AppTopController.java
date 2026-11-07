package com.jaycode.rongpet.module.post.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.module.post.dto.TopApplyRequest;
import com.jaycode.rongpet.module.post.service.TopService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序置顶：付费置顶与免费置顶申请（截图人工核验）。
 */
@Tag(name = "App-置顶")
@RestController
@RequestMapping("/app/v1/posts/{postId}/top")
@RequiredArgsConstructor
public class AppTopController {

    private final TopService topService;
    private final PayService payService;

    @Operation(summary = "申请免费置顶", description = "每帖一次，每账号每月上限见运营配置")
    @PostMapping("/free")
    public R<Void> applyFree(@PathVariable long postId,
                             @Valid @RequestBody TopApplyRequest request) {
        topService.applyFree(StpUserUtil.getLoginIdAsLong(), postId, request.getScreenshotUrl());
        return R.ok();
    }

    @Operation(summary = "付费置顶下单")
    @PostMapping("/pay")
    public R<PayInitVO> pay(@PathVariable long postId) {
        return R.ok(payService.createTopPay(StpUserUtil.getLoginIdAsLong(), postId));
    }
}
