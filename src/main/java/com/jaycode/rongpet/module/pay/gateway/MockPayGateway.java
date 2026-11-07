package com.jaycode.rongpet.module.pay.gateway;

import com.jaycode.rongpet.module.pay.entity.PayOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 本地 mock 支付通道：创建即视为支付成功，用于无商户凭证时的全链路联调。
 */
@Component
@ConditionalOnProperty(prefix = "rongpet.pay", name = "mock", havingValue = "true")
public class MockPayGateway implements PayGateway {

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public Map<String, String> pay(PayOrder order, String openid) {
        return Map.of();
    }

    @Override
    public void refund(PayOrder order, long refundAmount, String reason) {
        // mock 退款即时成功
    }
}
