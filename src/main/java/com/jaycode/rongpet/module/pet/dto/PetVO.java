package com.jaycode.rongpet.module.pet.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PetVO {

    private Long id;

    private String name;

    private String petType;

    private String breed;

    private String gender;

    private LocalDate birthDate;

    private BigDecimal weight;

    private String sterilized;

    private String vaccineStatus;

    private String chipFeature;

    private String avatarUrl;

    private String aiAvatarUrl;

    private List<String> photos;

    private String remark;

    private LocalDateTime createTime;
}
