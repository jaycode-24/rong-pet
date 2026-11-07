package com.jaycode.rongpet.module.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 风控专题用户视图：聚合同手机号/同证件多账号、单日批量发帖等异常信号，供运营人工核查。
 */
@Data
public class UserRiskVO {

    private Long userId;

    private String nickname;

    private String maskedPhone;

    private String status;

    /** 风险标签，前端直接展示 */
    private List<String> riskTags;

    /** 今日发帖数 */
    private long todayPostCount;

    /** 在途悬赏帖数 */
    private long rewardInFlight;

    /** 累计被举报次数 */
    private long reportCount;
}
