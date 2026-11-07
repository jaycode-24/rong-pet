package com.jaycode.rongpet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档按三端分组：小程序 / 后台 / 微信回调。生产环境通过配置或网关限制文档访问。
 */
@Configuration
public class OpenApiConfig {

    private static final String TOKEN_HEADER = "Authorization";

    @Bean
    public OpenAPI rongPetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("蓉宠帮平台 API")
                        .description("成都宠物本地信任平台：小程序端与运营后台接口")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(TOKEN_HEADER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(TOKEN_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(TOKEN_HEADER));
    }

    @Bean
    public GroupedOpenApi appGroup() {
        return GroupedOpenApi.builder()
                .group("app")
                .pathsToMatch("/app/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminGroup() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/admin/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi callbackGroup() {
        return GroupedOpenApi.builder()
                .group("callback")
                .pathsToMatch("/callback/**")
                .build();
    }
}
