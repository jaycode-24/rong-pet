package com.jaycode.rongpet.security.crypto;

import com.jaycode.rongpet.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        CryptoProperties properties = new CryptoProperties();
        // 恰好 32 字节的 AES-256 测试密钥
        properties.setAesKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        properties.setHmacKey(Base64.getEncoder().encodeToString("test-only-hmac-key".getBytes()));
        cryptoService = new CryptoService(properties);
    }

    @Test
    @DisplayName("AES-GCM 加解密往返一致，且两次加密密文不同（随机 IV）")
    void encryptAndDecryptRoundTrip() {
        String plain = "13800138000";

        String cipher1 = cryptoService.encrypt(plain);
        String cipher2 = cryptoService.encrypt(plain);

        assertThat(cipher1).isNotEqualTo(plain);
        assertThat(cipher1).isNotEqualTo(cipher2);
        assertThat(cryptoService.decrypt(cipher1)).isEqualTo(plain);
        assertThat(cryptoService.decrypt(cipher2)).isEqualTo(plain);
    }

    @Test
    @DisplayName("HMAC 索引稳定且不可逆，相同输入产生相同索引")
    void hmacIndexStable() {
        String h1 = cryptoService.hmacIndex("13800138000");
        String h2 = cryptoService.hmacIndex("13800138000");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
        assertThat(h1).doesNotContain("13800138000");
        assertThat(cryptoService.hmacIndex("13900139000")).isNotEqualTo(h1);
    }

    @Test
    @DisplayName("密文被篡改时 GCM 认证失败，抛出业务异常")
    void tamperedCipherThrows() {
        String cipher = cryptoService.encrypt("510100199001011234");
        char last = cipher.charAt(cipher.length() - 1);
        String tampered = cipher.substring(0, cipher.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> cryptoService.decrypt(tampered))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("空入参原样返回，可选字段不强制加密")
    void nullAndBlankPassthrough() {
        assertThat(cryptoService.encrypt(null)).isNull();
        assertThat(cryptoService.encrypt("")).isEmpty();
        assertThat(cryptoService.decrypt(null)).isNull();
        assertThat(cryptoService.hmacIndex(null)).isNull();
    }

    @Test
    @DisplayName("脱敏规则：手机号保留前3后4，身份证保留前4后4")
    void maskRules() {
        assertThat(CryptoService.maskPhone("13800138000")).isEqualTo("138****8000");
        assertThat(CryptoService.maskIdCard("510100199001011234")).isEqualTo("5101**********1234");
        assertThat(CryptoService.maskPhone("123")).isEqualTo("123");
    }

    @Test
    @DisplayName("密钥长度不合法时快速失败")
    void invalidKeyFailsFast() {
        CryptoProperties bad = new CryptoProperties();
        bad.setAesKey(Base64.getEncoder().encodeToString("short".getBytes()));
        bad.setHmacKey(Base64.getEncoder().encodeToString("k".getBytes()));

        assertThatThrownBy(() -> new CryptoService(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }
}
