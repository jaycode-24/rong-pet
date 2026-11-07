package com.jaycode.rongpet.module.pay.gateway;

import com.jaycode.rongpet.module.pay.entity.PayOrder;

import java.util.Map;

/**
 * 支付通道抽象：mock 用于本地联调，微信支付用于生产。回调统一进入 PayService 的幂等入账。
 */
public interface PayGateway {

    boolean mock();

    /** 发起支付，返回 JSAPI 调起参数；mock 通道返回空 Map */
    Map<String, String> pay(PayOrder order, String openid);

    /** 原路退款 */
    void refund(PayOrder order, long refundAmount, String reason);
}
