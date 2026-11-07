package com.jaycode.rongpet.module.pet.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 宠物档案，对应表 t_pet。用户私有数据，仅本人可见。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pet")
public class Pet extends BaseEntity {

    private Long userId;

    private String name;

    /** CAT/DOG/EXOTIC */
    private String petType;

    private String breed;

    /** MALE/FEMALE/UNKNOWN */
    private String gender;

    private LocalDate birthDate;

    /** 千克 */
    private java.math.BigDecimal weight;

    /** YES/NO/UNKNOWN */
    private String sterilized;

    /** DONE/PARTIAL/NONE */
    private String vaccineStatus;

    private String chipFeature;

    private String avatarUrl;

    private String aiAvatarUrl;

    /** 照片 JSON 数组 */
    private String photos;

    private String remark;
}
