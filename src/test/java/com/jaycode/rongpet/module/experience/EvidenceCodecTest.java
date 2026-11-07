package com.jaycode.rongpet.module.experience;

import com.jaycode.rongpet.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceCodecTest {

    @Test
    @DisplayName("encode 输出 JSON 数组，decode 往返一致")
    void encodeAndDecodeRoundTrip() {
        String json = EvidenceCodec.encode(List.of("https://a/1.jpg", "https://a/2.jpg"));

        assertThat(json).startsWith("[").endsWith("]");
        assertThat(EvidenceCodec.decode(json)).containsExactly("https://a/1.jpg", "https://a/2.jpg");
    }

    @Test
    @DisplayName("decode 兼容 V7 保存的单个非数组地址，不重复包装")
    void decodeLegacySingleValue() {
        assertThat(EvidenceCodec.decode("https://a/legacy.jpg")).containsExactly("https://a/legacy.jpg");
        assertThat(EvidenceCodec.decode("  ")).isEmpty();
        assertThat(EvidenceCodec.decode(null)).isEmpty();
    }

    @Test
    @DisplayName("decode 遇到嵌套对象或数组时抛业务异常")
    void rejectNestedStructure() {
        assertThatThrownBy(() -> EvidenceCodec.decode("[\"a\", [\"b\"]]"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("encode 拒绝空集合、超过9张与空白地址")
    void rejectInvalidInput() {
        assertThatThrownBy(() -> EvidenceCodec.encode(List.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> EvidenceCodec.encode(List.of(" "))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> EvidenceCodec.encode(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")))
                .isInstanceOf(BizException.class);
    }
}
