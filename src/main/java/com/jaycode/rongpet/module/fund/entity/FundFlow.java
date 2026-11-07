package com.jaycode.rongpet.module.fund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资金流水，对应表 t_fund_flow。金额为分，收支方向 IN/OUT。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_fund_flow")
public class FundFlow extends BaseEntity {

    private String flowNo;

    private Long userId;

    /** REWARD/SPLIT/GRATUITY/TOP/REFUND */
    private String bizType;

    private String bizNo;

    /** IN/OUT */
    private String direction;

    private Long amount;

    /** PROCESSING/SUCCESS/FAILED */
    private String status;

    private String voucherNo;

    private String operator;

    private String remark;

    private LocalDateTime finishTime;
}
