package com.jaycode.rongpet.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 腾讯云 COS 装配。图片统一存私有 bucket，通过预签名 URL 访问（PRD 4.4）。
 * 仅当 SecretId/SecretKey 均配置为非空时才创建客户端；本地无凭证时不装配，
 * 由 FileStorageService 回退到本地目录存储。
 */
@Configuration
@EnableConfigurationProperties(CosProperties.class)
public class CosConfig {

    /** 两个永久密钥都非空才启用 COS；空字符串视为未配置（@ConditionalOnProperty 无法区分空串）。 */
    static class OnCosCredentialsCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return StringUtils.hasText(env.getProperty("rongpet.cos.secret-id"))
                    && StringUtils.hasText(env.getProperty("rongpet.cos.secret-key"));
        }
    }

    @Bean(destroyMethod = "shutdown")
    @Conditional(OnCosCredentialsCondition.class)
    public COSClient cosClient(CosProperties properties) {
        // 永久密钥用 BasicCOSCredentials；仅临时密钥（含 SessionToken）才用 BasicSessionCredentials。
        COSCredentials credentials = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(credentials, clientConfig);
    }
}
