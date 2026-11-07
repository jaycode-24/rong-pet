package com.jaycode.rongpet.module.pay.error;

import com.jaycode.rongpet.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum PayErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("PAY_ORDER_NOT_FOUND", "支付单不存在"),
    ORDER_STATUS_INVALID("PAY_ORDER_STATUS_INVALID", "支付单状态不允许该操作"),
    REWARD_ALREADY_PAID("REWARD_ALREADY_PAID", "该帖子悬赏已支付"),
    REWARD_NOT_PAYABLE("REWARD_NOT_PAYABLE", "当前帖子不可支付悬赏"),
    WXPAY_NOT_CONFIGURED("WXPAY_NOT_CONFIGURED", "微信支付未配置，暂不可支付"),
    WXPAY_ERROR("WXPAY_ERROR", "微信支付调用失败，请稍后重试"),
    REAL_NAME_REQUIRED("REAL_NAME_REQUIRED", "悬赏金额超过5000元，请先完成实名补充"),
    USER_FORBIDDEN("PAY_USER_FORBIDDEN", "当前账号状态不可支付"),
    REFUND_NOT_ALLOWED("REFUND_NOT_ALLOWED", "当前支付单不可退款"),
    AMOUNT_MISMATCH("PAY_AMOUNT_MISMATCH", "支付金额与订单不一致"),
    TOP_NOT_PAYABLE("TOP_NOT_PAYABLE", "该帖子当前不可购买置顶");

    private final String code;
    private final String message;

    PayErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
