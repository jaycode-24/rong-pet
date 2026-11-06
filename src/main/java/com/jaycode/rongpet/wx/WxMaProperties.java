package com.jaycode.rongpet.wx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rongpet.wx.ma")
public class WxMaProperties {

    /** 小程序 AppID */
    private String appid;

    /** 小程序 AppSecret */
    private String secret;
}
