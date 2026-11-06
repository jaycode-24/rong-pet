package com.jaycode.rongpet.cos;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rongpet.cos")
public class CosProperties {

    private String secretId;
    private String secretKey;
    /** 地域，如 ap-chengdu */
    private String region;
    /** 存储桶名称，如 rong-pet-1300000000 */
    private String bucket;
    /** 对象 key 统一前缀 */
    private String prefix;
    /** 预签名 URL 有效期（秒） */
    private long presignExpireSeconds = 600;
}
