package com.jaycode.rongpet.wx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rongpet.wx.pay")
public class WxPayProperties {

    private String appId;
    private String mchId;
    /** APIv2 密钥（部分老接口仍需使用） */
    private String mchKey;
    /** APIv3 密钥 */
    private String apiV3Key;
    /** 商户证书序列号 */
    private String certSerialNo;
    /** 商户 API 私钥文件路径（apiclient_key.pem） */
    private String privateKeyPath;
    /** 支付回调基础地址，具体路径由各业务模块拼接 */
    private String notifyUrl;
}
