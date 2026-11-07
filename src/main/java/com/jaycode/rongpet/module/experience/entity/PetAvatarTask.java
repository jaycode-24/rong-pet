package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pet_avatar_task")
public class PetAvatarTask extends BaseEntity {
    private Long userId;

    private Long petId;

    private String requestKey;

    private String style;

    private String sourceUrl;

    private Integer freeUse;

    private Long price;

    private String status;

    private String resultUrl;

    private String errorMessage;

    private String orderNo;

    private Integer payRebuildCount;

}
