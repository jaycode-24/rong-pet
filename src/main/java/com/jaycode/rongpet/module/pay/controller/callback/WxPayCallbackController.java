package com.jaycode.rongpet.module.pay.controller.callback;

import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.service.WxPayService;
import com.jaycode.rongpet.module.pay.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 V3 回调。
 * 验签由 WxPayService 完成（平台证书自动刷新），业务入账统一走 PayService 的幂等处理；
 * 重复通知必须返回 SUCCESS，避免微信持续重试。
 */
@Slf4j
@Tag(name = "Callback-微信支付")
@RestController
@RequestMapping("/callback/wxpay")
@RequiredArgsConstructor
public class WxPayCallbackController {

    private final ObjectProvider<WxPayService> wxPayServiceProvider;
    private final PayService payService;

    @Operation(summary = "支付结果通知")
    @PostMapping("/order")
    public ResponseEntity<Map<String, String>> orderNotify(
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestBody String body) {
        WxPayService wxPayService = wxPayServiceProvider.getIfAvailable();
        if (wxPayService == null) {
            log.error("收到微信支付回调但未配置微信支付通道");
            return fail("微信支付未配置");
        }
        try {
            SignatureHeader header = SignatureHeader.builder()
                    .timeStamp(timestamp)
                    .nonce(nonce)
                    .signature(signature)
                    .serial(serial)
                    .build();
            WxPayNotifyV3Result notifyResult = wxPayService.parseOrderNotifyV3Result(body, header);
            WxPayNotifyV3Result.DecryptNotifyResult result = notifyResult.getResult();
            if (result == null) {
                return fail("回调内容为空");
            }
            if (!"SUCCESS".equals(result.getTradeState())) {
                log.warn("微信支付回调非成功状态 outTradeNo={}, state={}",
                        result.getOutTradeNo(), result.getTradeState());
                return success();
            }
            long paidTotal = result.getAmount() == null || result.getAmount().getPayerTotal() == null
                    ? 0L : result.getAmount().getPayerTotal().longValue();
            boolean handled = payService.onCallbackPaid(result.getOutTradeNo(), result.getTransactionId(), paidTotal);
            return handled ? success() : fail("支付单处理失败");
        } catch (Exception e) {
            log.error("微信支付回调处理异常", e);
            return fail("回调处理异常");
        }
    }

    private ResponseEntity<Map<String, String>> success() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "SUCCESS");
        body.put("message", "成功");
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, String>> fail(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "FAIL");
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
