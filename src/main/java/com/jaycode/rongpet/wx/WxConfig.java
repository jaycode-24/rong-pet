package com.jaycode.rongpet.wx;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 微信能力装配。未在配置中提供有效凭证（缺失或空串）时不创建 Bean，
 * 业务层通过 ObjectProvider 注入，配合 mock 通道完成本地联调。
 */
@Configuration
@EnableConfigurationProperties({WxMaProperties.class, WxPayProperties.class})
public class WxConfig {

    @Bean
    @Conditional(OnMaCredentialsCondition.class)
    public WxMaService wxMaService(WxMaProperties properties) {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(properties.getAppid());
        config.setSecret(properties.getSecret());
        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }

    @Bean
    @Conditional(OnPayCredentialsCondition.class)
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

    static class OnMaCredentialsCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return StringUtils.hasText(env.getProperty("rongpet.wx.ma.appid"))
                    && StringUtils.hasText(env.getProperty("rongpet.wx.ma.secret"));
        }
    }

    static class OnPayCredentialsCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return StringUtils.hasText(env.getProperty("rongpet.wx.pay.app-id"))
                    && StringUtils.hasText(env.getProperty("rongpet.wx.pay.mch-id"));
        }
    }
}
