package com.jaycode.rongpet.module.post.dto;

import com.jaycode.rongpet.module.split.dto.SplitSummaryVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDetailVO {

    private Long id;

    private String type;

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

    private String district;

    private String location;

    private String locationLatLng;

    private LocalDateTime eventTime;

    private String description;

    private List<String> images;

    private String coverUrl;

    private Long rewardAmount;

    private String rewardStatus;

    private Integer rewardDays;

    private LocalDateTime rewardExpireTime;

    private Long gratuityAmount;

    private String status;

    private String rejectReason;

    private LocalDateTime topExpireTime;

    private LocalDateTime expireTime;

    private Integer viewCount;

    private Integer clueCount;

    private Integer claimCount;

    private LocalDateTime foundTime;

    private String foundNote;

    private List<String> foundProof;

    private String publisherNickname;

    /** 脱敏联系方式 */
    private String contactMasked;

    private boolean owner;

    private LocalDateTime createTime;

    /** 分账汇总（仅已完成/分账中可见，明细按当事人可见性裁剪） */
    private SplitSummaryVO splitSummary;
}
