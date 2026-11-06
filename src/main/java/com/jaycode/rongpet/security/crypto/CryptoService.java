package com.jaycode.rongpet.security.crypto;

import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 敏感字段加解密服务。
 * <ul>
 *   <li>加密：AES-256-GCM，每次随机 12 字节 IV，输出 Base64(IV + 密文 + 认证标签)</li>
 *   <li>检索索引：HMAC-SHA256 十六进制串，用于手机号/身份证等值查重与反作弊比对</li>
 *   <li>展示脱敏：手机号、身份证按角色在序列化层处理</li>
 * </ul>
 */
@Slf4j
public class CryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec aesKeySpec;
    private final SecretKeySpec hmacKeySpec;

    public CryptoService(CryptoProperties properties) {
        byte[] aesBytes = decodeKey(properties.getAesKey(), "aes-key");
        if (aesBytes.length != AES_KEY_BYTES) {
            throw new IllegalStateException("rongpet.crypto.aes-key 解码后必须为 32 字节（AES-256）");
        }
        this.aesKeySpec = new SecretKeySpec(aesBytes, "AES");
        this.hmacKeySpec = new SecretKeySpec(decodeKey(properties.getHmacKey(), "hmac-key"), "HmacSHA256");
    }

    @PostConstruct
    void init() {
        log.info("CryptoService 初始化完成，敏感字段加密已启用");
    }

    /** 加密明文，返回 Base64 字符串；入参为空时原样返回，便于可选字段处理 */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new BizException(CommonErrorCode.CRYPTO_ERROR, "加密失败", e);
        }
    }

    /** 解密 encrypt 产生的密文 */
    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BizException(CommonErrorCode.CRYPTO_ERROR, "解密失败", e);
        }
    }

    /** 生成等值检索用 HMAC 索引（手机号、身份证、收款账户等） */
    public String hmacIndex(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKeySpec);
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new BizException(CommonErrorCode.CRYPTO_ERROR, "HMAC 计算失败", e);
        }
    }

    /** 手机号脱敏：138****1234 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 身份证脱敏：5101**********1234 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        int prefix = 4;
        int suffix = 4;
        return idCard.substring(0, prefix) + "*".repeat(idCard.length() - prefix - suffix)
                + idCard.substring(idCard.length() - suffix);
    }

    private byte[] decodeKey(String base64Key, String name) {
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException("rongpet.crypto." + name + " 未配置，敏感数据组件无法启动");
        }
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("rongpet.crypto." + name + " 不是合法的 Base64 字符串", e);
        }
    }
}
