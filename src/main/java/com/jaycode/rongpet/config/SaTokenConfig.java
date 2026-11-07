package com.jaycode.rongpet.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.jaycode.rongpet.security.StpAdminUtil;
import com.jaycode.rongpet.security.StpUserUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由鉴权配置。
 * 小程序端与后台端路径、账号体系完全隔离：/app/v1/** 校验 user 会话，/admin/v1/** 校验 admin 会话，
 * /callback/** 为验签回调入口不依赖会话。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUserUtil.checkLogin()))
                .addPathPatterns("/app/v1/**")
                .excludePathPatterns("/app/v1/auth/login", "/app/v1/public/**");

        registry.addInterceptor(new SaInterceptor(handle -> StpAdminUtil.checkLogin()))
                .addPathPatterns("/admin/v1/**")
                .excludePathPatterns("/admin/v1/auth/login");
    }
}
