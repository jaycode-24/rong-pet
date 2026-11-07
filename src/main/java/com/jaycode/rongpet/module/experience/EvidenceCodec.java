package com.jaycode.rongpet.module.experience;

import cn.hutool.json.JSONUtil;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import java.util.List;

/** 新请求统一数组；兼容V7已保存的单个地址或JSON数组，不重复包装。 */
public final class EvidenceCodec {
    private EvidenceCodec() {}

    public static List<String> decode(String value) {
        if (value == null || value.isBlank()) return List.of();
        String text = value.trim();
        if (!text.startsWith("[")) return List.of(text);
        try {
            List<Object> items = JSONUtil.parseArray(text).toList(Object.class);
            if (items.stream().anyMatch(item -> !(item instanceof String))) throw new IllegalArgumentException();
            return items.stream().map(String.class::cast).toList();
        } catch (Exception error) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "证据必须是图片地址数组，不能包含嵌套数组或对象");
        }
    }

    public static String encode(List<String> evidence) {
        if (evidence == null || evidence.isEmpty() || evidence.size() > 9
                || evidence.stream().anyMatch(item -> item == null || item.isBlank() || item.length() > 1024))
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请提交1至9个有效证据地址");
        return JSONUtil.toJsonStr(evidence);
    }
}
