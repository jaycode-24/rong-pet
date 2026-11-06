package com.jaycode.rongpet.security.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加解密密钥配置。生产环境必须通过环境变量 RONGPET_CRYPTO_AES_KEY / RONGPET_CRYPTO_HMAC_KEY 注入。
 */
@Data
@ConfigurationProperties(prefix = "rongpet.crypto")
public class CryptoProperties {

    /** AES-256 密钥，Base64 编码，解码后须为 32 字节 */
    private String aesKey;

    /** HMAC-SHA256 密钥，Base64 编码 */
    private String hmacKey;
}
