package com.jaycode.rongpet.module.pay.controller.app;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序支付：悬赏支付下单与我的支付单。
 * 置顶支付见 /app/v1/posts/{postId}/top/pay，感谢金见 /app/v1/posts/{postId}/gratuity。
 */
@Tag(name = "App-支付")
@RestController
@RequestMapping("/app/v1/pay")
@RequiredArgsConstructor
public class AppPayController {

    private final PayService payService;

    @Operation(summary = "悬赏支付下单", description = "审核通过的寻宠帖支付本金+服务费后进入托管")
    @PostMapping("/reward/{postId}")
    public R<PayInitVO> reward(@PathVariable long postId) {
        return R.ok(payService.createRewardPay(StpUserUtil.getLoginIdAsLong(), postId));
    }

    @Operation(summary = "我的支付单")
    @GetMapping("/orders")
    public R<PageResult<PayOrder>> orders(@RequestParam(required = false) String bizType,
                                          @RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(payService.mine(StpUserUtil.getLoginIdAsLong(), bizType, pageNum, pageSize));
    }
}
