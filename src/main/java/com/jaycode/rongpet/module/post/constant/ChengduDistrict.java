package com.jaycode.rongpet.module.post.constant;

import java.util.List;

/**
 * 成都市行政区划（V1.0 唯一业务地域）。发布与筛选只允许本清单内的区县。
 */
public final class ChengduDistrict {

    public static final List<String> ALL = List.of(
            "锦江区", "青羊区", "金牛区", "武侯区", "成华区", "龙泉驿区", "青白江区", "新都区",
            "温江区", "双流区", "郫都区", "新津区", "简阳市", "都江堰市", "彭州市", "邛崃市",
            "崇州市", "金堂县", "大邑县", "蒲江县", "高新区", "天府新区");

    private ChengduDistrict() {
    }

    public static boolean isValid(String district) {
        return district != null && ALL.contains(district);
    }
}
