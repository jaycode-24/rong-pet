package com.jaycode.rongpet.security;

import com.jaycode.rongpet.module.config.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceTest {

    @Mock
    private SysConfigService sysConfigService;

    @InjectMocks
    private SensitiveWordService sensitiveWordService;

    @BeforeEach
    void setUp() {
        // 配置为空时回退内置词库
        when(sysConfigService.get(SensitiveWordService.CONFIG_KEY, "")).thenReturn("");
    }

    @Test
    @DisplayName("命中内置违规词：活体买卖/导流类")
    void hitBuiltinWords() {
        List<String> hits = sensitiveWordService.scan("家里小猫出售幼犬，可加微信详谈");

        assertThat(hits).anyMatch(h -> h.contains("出售幼犬") || h.contains("加微信"));
        assertThat(sensitiveWordService.containsSensitive("提供贷款刷单兼职")).isTrue();
    }

    @Test
    @DisplayName("正常互助内容不命中")
    void cleanContentPasses() {
        assertThat(sensitiveWordService.scan("猫咪在锦江区走丢，特征是橘色短毛，体型偏胖")).isEmpty();
        assertThat(sensitiveWordService.containsSensitive("感谢好心人帮忙找回宠物")).isFalse();
    }

    @Test
    @DisplayName("纯手机号/QQ 导流特征识别")
    void detectContactDiversion() {
        assertThat(sensitiveWordService.scan("联系我 13812345678")).contains("疑似手机号导流");
        assertThat(sensitiveWordService.scan("加我QQ：123456789")).contains("疑似QQ导流");
        // 联系方式中允许的标准 11 位手机号场景由调用方字段承载，描述文本内的裸号码仍判导流
        assertThat(sensitiveWordService.scan("无任何联系方式的普通描述")).isEmpty();
    }

    @Test
    @DisplayName("运营配置词库优先于内置词库，支持自定义拦截词")
    void customWordsOverride() {
        when(sysConfigService.get(SensitiveWordService.CONFIG_KEY, "")).thenReturn("自定义违禁词,另一个词");

        assertThat(sensitiveWordService.scan("这里包含自定义违禁词")).contains("自定义违禁词");
        // 自定义词库生效后不再命中内置词（词库整体替换）
        assertThat(sensitiveWordService.scan("加微信")).doesNotContain("加微信");
    }

    @Test
    @DisplayName("空文本安全返回空命中")
    void blankText() {
        assertThat(sensitiveWordService.scan("", null)).isEmpty();
    }
}
