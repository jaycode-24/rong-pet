package com.jaycode.rongpet.module.config.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运营配置项，对应表 t_sys_config。费率/价格/阈值等业务参数一律走本表，禁止硬编码。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_config")
public class SysConfig extends BaseEntity {

    private String configKey;

    private String configValue;

    private String remark;
}
