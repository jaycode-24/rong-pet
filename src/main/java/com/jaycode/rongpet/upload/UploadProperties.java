package com.jaycode.rongpet.upload;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片上传配置。生产强制使用腾讯云 COS 私有桶；本地联调可开启本地目录兜底（禁止用于生产）。
 */
@Data
@ConfigurationProperties(prefix = "rongpet.upload")
public class UploadProperties {

    /** 单张图片大小上限（字节），默认 5MB */
    private long maxSize = 5L * 1024 * 1024;

    /** 本地联调兜底开关，仅 dev 使用；生产保持 false */
    private boolean localEnabled = false;

    /** 本地文件根目录 */
    private String localDir = "./data/uploads";

    /** 本地文件访问前缀，需与静态资源映射一致 */
    private String localBaseUrl = "/files";
}
