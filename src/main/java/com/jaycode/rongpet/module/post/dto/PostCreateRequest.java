package com.jaycode.rongpet.module.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布/编辑帖子请求（寻宠、招领、相亲共用）。
 * 宠物基础字段可在选择档案后自动填充，修改不同步回档案。
 */
@Data
public class PostCreateRequest {

    @NotBlank(message = "帖子类型不能为空")
    private String type;

    private Long petId;

    private String title;

    private String petType;

    private String breed;

    private String gender;

    private String color;

    private String size;

    private String sterilized;

    private String chipFeature;

    private BigDecimal age;

    private BigDecimal weight;

    private String vaccineStatus;

    private String dewormStatus;

    private String geneticScreening;

    private String breedIntent;

    private String expectCondition;

    @NotBlank(message = "区县不能为空")
    private String district;

    @NotBlank(message = "地点不能为空")
    private String location;

    private String locationLatLng;

    @NotNull(message = "发生时间不能为空")
    private LocalDateTime eventTime;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    @NotBlank(message = "详细描述不能为空")
    private String description;

    @NotEmpty(message = "至少上传1张图片")
    private List<String> images;

    /** 悬赏本金（分），仅寻宠可选 */
    private Long rewardAmount;

    /** 悬赏有效期：7/15/30 天 */
    private Integer rewardDays;

    /** 是否勾选协议 */
    private Boolean agreed;

    private String agreementVersion;

    /** 无档案时是否保存为宠物档案 */
    private Boolean saveAsPet;

    /** 草稿标记 */
    private Boolean draft;
}
