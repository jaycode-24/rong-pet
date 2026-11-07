package com.jaycode.rongpet.module.feeder.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 喂养员资料设置：可服务区县、服务介绍、按周时段、参考价格、押金意愿。
 */
@Data
public class FeederProfileRequest {

    private List<String> serviceDistricts;

    /** 50-500 字 */
    private String serviceIntro;

    /** 按周时段：[{weekday:1, slots:["08:00-10:00"]}] */
    private List<Map<String, Object>> serviceTimes;

    /** 单次参考价（分） */
    private Long priceOnce;

    /** 单日参考价（分） */
    private Long priceDaily;

    private Boolean depositAccepted;
}
