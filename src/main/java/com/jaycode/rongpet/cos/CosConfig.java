package com.jaycode.rongpet.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 装配。图片统一存私有 bucket，通过预签名 URL 访问（PRD 4.4）。
 */
@Configuration
@EnableConfigurationProperties(CosProperties.class)
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "rongpet.cos", name = "secret-id")
    public COSClient cosClient(CosProperties properties) {
        COSCredentials credentials = new BasicSessionCredentials(
                properties.getSecretId(), properties.getSecretKey(), null);
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(credentials, clientConfig);
    }
}
