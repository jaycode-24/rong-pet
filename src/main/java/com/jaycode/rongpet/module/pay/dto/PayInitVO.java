package com.jaycode.rongpet.module.pay.dto;

import lombok.Data;

import java.util.Map;

/**
 * 支付发起结果。mock 模式 paid=true 直接完成；微信模式返回 JSAPI 调起参数。
 */
@Data
public class PayInitVO {

    private String outTradeNo;

    private long total;

    private String status;

    private boolean paid;

    /** JSAPI 调起参数：appId/timeStamp/nonceStr/package/signType/paySign */
    private Map<String, String> payParams;
}
