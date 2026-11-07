package com.jaycode.rongpet.security;

import com.jaycode.rongpet.module.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感词/风险内容拦截（PRD FR-4.3）。
 * 覆盖类别：活体买卖与领养收费、宠物医疗与药品、代办证件、贷款刷单、外部联系方式导流、涉黄涉赌涉政。
 * 命中词库的内容不直接对外展示，统一进入人工复核队列（帖子保持 PENDING_AUDIT 并打标）。
 * 词库存放于运营配置 content.sensitive.words（逗号/换行/顿号分隔），后台可即时调整；
 * 内置基础词库兜底，微信内容安全接口凭证就绪后在此叠加 msgSecCheck 结果。
 */
@Component
@RequiredArgsConstructor
public class SensitiveWordService {

    public static final String CONFIG_KEY = "content.sensitive.words";

    /** 内置兜底词库（运营配置为空时使用） */
    private static final List<String> BUILTIN = List.of(
            "活体买卖", "活体交易", "有偿领养", "领养费", "出售幼犬", "出售幼猫", "包配种",
            "宠物医疗", "兽药", "特效药", "代办证", "办证", "刻章",
            "贷款", "刷单", "兼职刷单", "套现",
            "加微信", "加v", "加V", "扫码进群", "二维码",
            "赌博", "博彩", "色情", "上门服务");

    /** 纯 11 位手机号、连续 QQ 号导流的弱特征（外部联系方式导流） */
    private static final Pattern PLAIN_PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern QQ_CONTACT = Pattern.compile("(?i)(qq|企鹅)[:：\\s]{0,3}\\d{5,12}");

    private final SysConfigService sysConfigService;

    /**
     * 扫描多段文本，返回命中的风险词与导流特征（去重）。空列表表示未命中。
     */
    public List<String> scan(String... texts) {
        String joined = StringUtils.arrayToDelimitedString(texts, "\n");
        if (!StringUtils.hasText(joined)) {
            return List.of();
        }
        String normalized = joined.replace(" ", "").toLowerCase();
        List<String> hits = new ArrayList<>();
        for (String word : words()) {
            String w = word.trim();
            if (!w.isEmpty() && normalized.contains(w.toLowerCase())) {
                hits.add(w);
            }
        }
        if (PLAIN_PHONE.matcher(joined).find()) {
            hits.add("疑似手机号导流");
        }
        if (QQ_CONTACT.matcher(joined).find()) {
            hits.add("疑似QQ导流");
        }
        return hits.stream().distinct().toList();
    }

    /** 是否命中敏感词 */
    public boolean containsSensitive(String... texts) {
        return !scan(texts).isEmpty();
    }

    private List<String> words() {
        String config = sysConfigService.get(CONFIG_KEY, "");
        if (!StringUtils.hasText(config)) {
            return BUILTIN;
        }
        List<String> result = new ArrayList<>();
        for (String part : config.split("[,，、;；\n\r\t]+")) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result.isEmpty() ? BUILTIN : result;
    }
}
