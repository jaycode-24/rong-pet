package com.jaycode.rongpet.module.pay.gateway;

import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.error.PayErrorCode;
import com.jaycode.rongpet.wx.WxPayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 V3 JSAPI 通道。未配置商户凭证时拒绝下单，避免出现"假支付"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rongpet.pay", name = "mock", havingValue = "false", matchIfMissing = true)
public class WxPayGateway implements PayGateway {

    private final ObjectProvider<WxPayService> wxPayServiceProvider;
    private final WxPayProperties wxPayProperties;

    @Override
    public boolean mock() {
        return false;
    }

    @Override
    public Map<String, String> pay(PayOrder order, String openid) {
        WxPayService wxPayService = requireService();
        WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
        request.setAppid(wxPayProperties.getAppId());
        request.setMchid(wxPayProperties.getMchId());
        request.setOutTradeNo(order.getOutTradeNo());
        request.setDescription(description(order));
        request.setNotifyUrl(wxPayProperties.getNotifyUrl());
        WxPayUnifiedOrderV3Request.Amount amount = new WxPayUnifiedOrderV3Request.Amount();
        amount.setTotal(Math.toIntExact(order.getTotal()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        WxPayUnifiedOrderV3Request.Payer payer = new WxPayUnifiedOrderV3Request.Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);
        try {
            WxPayUnifiedOrderV3Result.JsapiResult result =
                    wxPayService.createOrderV3(TradeTypeEnum.JSAPI, request);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("appId", result.getAppId());
            params.put("timeStamp", result.getTimeStamp());
            params.put("nonceStr", result.getNonceStr());
            params.put("package", result.getPackageValue());
            params.put("signType", result.getSignType());
            params.put("paySign", result.getPaySign());
            return params;
        } catch (WxPayException e) {
            log.error("微信统一下单失败 outTradeNo={}", order.getOutTradeNo(), e);
            throw new BizException(PayErrorCode.WXPAY_ERROR);
        }
    }

    @Override
    public void refund(PayOrder order, long refundAmount, String reason) {
        WxPayService wxPayService = requireService();
        WxPayRefundV3Request request = new WxPayRefundV3Request();
        request.setOutTradeNo(order.getOutTradeNo());
        request.setOutRefundNo("RF" + order.getOutTradeNo());
        request.setReason(reason);
        request.setNotifyUrl(wxPayProperties.getNotifyUrl());
        WxPayRefundV3Request.Amount amount = new WxPayRefundV3Request.Amount();
        amount.setRefund(Math.toIntExact(refundAmount));
        amount.setTotal(Math.toIntExact(order.getTotal()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        try {
            wxPayService.refundV3(request);
        } catch (WxPayException e) {
            log.error("微信退款失败 outTradeNo={}", order.getOutTradeNo(), e);
            throw new BizException(PayErrorCode.WXPAY_ERROR);
        }
    }

    private WxPayService requireService() {
        WxPayService service = wxPayServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(PayErrorCode.WXPAY_NOT_CONFIGURED);
        }
        return service;
    }

    private String description(PayOrder order) {
        return switch (order.getBizType()) {
            case "REWARD" -> "蓉宠帮-悬赏托管";
            case "TOP" -> "蓉宠帮-帖子置顶";
            case "GRATUITY" -> "蓉宠帮-感谢金";
            default -> "蓉宠帮-服务";
        };
    }
}
