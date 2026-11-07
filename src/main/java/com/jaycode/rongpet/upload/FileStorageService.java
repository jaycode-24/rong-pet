package com.jaycode.rongpet.upload;

import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.cos.CosProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传服务（PRD FR-4.4）：单张 ≤5MB，仅 jpg/jpeg/png，客户端压缩后上传。
 * 生产使用腾讯云 COS 私有桶，返回预签名 URL；本地无 COS 凭证时可开启本地目录兜底。
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final CosProperties cosProperties;
    private final UploadProperties uploadProperties;
    private final ObjectProvider<COSClient> cosClientProvider;

    public FileStorageService(CosProperties cosProperties, UploadProperties uploadProperties,
                              ObjectProvider<COSClient> cosClientProvider) {
        this.cosProperties = cosProperties;
        this.uploadProperties = uploadProperties;
        this.cosClientProvider = cosClientProvider;
    }

    public UploadVO upload(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "单张图片不能超过 5MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "仅支持 jpg、jpeg、png 格式");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "文件类型不合法");
        }
        String safeCategory = StringUtils.hasText(category) && category.matches("[a-zA-Z0-9_-]{1,32}")
                ? category : "common";
        String key = buildKey(safeCategory, extension);

        COSClient cosClient = cosClientProvider.getIfAvailable();
        UploadVO vo = new UploadVO();
        vo.setKey(key);
        vo.setContentType(contentType == null ? "image/" + ("png".equals(extension) ? "png" : "jpeg") : contentType);
        vo.setSize(file.getSize());
        if (cosClient != null && StringUtils.hasText(cosProperties.getBucket())) {
            vo.setUrl(uploadToCos(cosClient, key, file));
            return vo;
        }
        if (uploadProperties.isLocalEnabled()) {
            vo.setUrl(uploadToLocal(key, file));
            return vo;
        }
        throw new BizException(CommonErrorCode.SYSTEM_ERROR, "图片存储未配置，请联系管理员");
    }

    private String buildKey(String category, String extension) {
        String prefix = StringUtils.hasText(cosProperties.getPrefix()) ? cosProperties.getPrefix() : "rong-pet";
        return prefix + "/" + category + "/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private String uploadToCos(COSClient cosClient, String key, MultipartFile file) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucket(), key, inputStream, metadata);
            cosClient.putObject(request);
            URL url = cosClient.generatePresignedUrl(cosProperties.getBucket(), key,
                    new java.util.Date(System.currentTimeMillis() + cosProperties.getPresignExpireSeconds() * 1000));
            return url.toString();
        } catch (IOException e) {
            log.error("图片上传 COS 失败 key={}", key, e);
            throw new BizException(CommonErrorCode.SYSTEM_ERROR, "图片上传失败，请稍后重试");
        }
    }

    private String uploadToLocal(String key, MultipartFile file) {
        try {
            Path root = Paths.get(uploadProperties.getLocalDir()).toAbsolutePath().normalize();
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "非法的文件路径");
            }
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return uploadProperties.getLocalBaseUrl() + "/" + key;
        } catch (IOException e) {
            log.error("图片本地保存失败 key={}", key, e);
            throw new BizException(CommonErrorCode.SYSTEM_ERROR, "图片上传失败，请稍后重试");
        }
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
