package com.jaycode.rongpet.wx;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信能力装配。未在配置中提供凭证时不创建 Bean，
 * 业务层通过 ObjectProvider 或 @Autowired(required = false) 注入，配合 M0 阶段的兼容性验证。
 */
@Configuration
@EnableConfigurationProperties({WxMaProperties.class, WxPayProperties.class})
public class WxConfig {

    @Bean
    @ConditionalOnProperty(prefix = "rongpet.wx.ma", name = "appid")
    public WxMaService wxMaService(WxMaProperties properties) {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(properties.getAppid());
        config.setSecret(properties.getSecret());
        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }

    @Bean
    @ConditionalOnProperty(prefix = "rongpet.wx.pay", name = "mch-id")
    public WxPayService wxPayService(WxPayProperties properties) {
        WxPayConfig config = new WxPayConfig();
        config.setAppId(properties.getAppId());
        config.setMchId(properties.getMchId());
        config.setMchKey(properties.getMchKey());
        config.setApiV3Key(properties.getApiV3Key());
        config.setCertSerialNo(properties.getCertSerialNo());
        config.setPrivateKeyPath(properties.getPrivateKeyPath());
        config.setNotifyUrl(properties.getNotifyUrl());
        WxPayService service = new WxPayServiceImpl();
        service.setConfig(config);
        return service;
    }
}
