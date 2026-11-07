package com.jaycode.rongpet.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 本地图片兜底访问映射，仅在开启 rongpet.upload.local-enabled 时生效；生产使用 COS 预签名 URL。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(UploadProperties.class)
public class UploadWebConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!uploadProperties.isLocalEnabled()) {
            return;
        }
        String location = Paths.get(uploadProperties.getLocalDir()).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(uploadProperties.getLocalBaseUrl() + "/**")
                .addResourceLocations(location);
    }
}
