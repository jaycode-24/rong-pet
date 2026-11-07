package com.jaycode.rongpet.module.gratuity.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.gratuity.dto.GratuityPayRequest;
import com.jaycode.rongpet.module.gratuity.dto.GratuityVO;
import com.jaycode.rongpet.module.gratuity.service.GratuityService;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序感谢金：仅已确认认领的失主可支付，支付后冻结待平台核验转付。
 */
@Tag(name = "App-感谢金")
@RestController
@RequestMapping("/app/v1/posts/{postId}/gratuity")
@RequiredArgsConstructor
public class AppGratuityController {

    private final GratuityService gratuityService;

    @Operation(summary = "支付感谢金", description = "金额单位分，收款账户加密存储")
    @PostMapping
    public R<PayInitVO> pay(@PathVariable long postId,
                            @Valid @RequestBody GratuityPayRequest request) {
        return R.ok(gratuityService.createAndPay(StpUserUtil.getLoginIdAsLong(), postId, request));
    }

    @Operation(summary = "我支付的感谢金")
    @GetMapping
    public R<GratuityVO> mine(@PathVariable long postId) {
        return R.ok(gratuityService.myGratuity(StpUserUtil.getLoginIdAsLong(), postId));
    }
}
