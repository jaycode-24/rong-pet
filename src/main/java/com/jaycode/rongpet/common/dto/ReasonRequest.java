package com.jaycode.rongpet.common.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通用原因/备注请求：拒绝、关闭、退款等只需一句说明的场景。
 */
@Data
public class ReasonRequest {

    @Size(max = 255, message = "原因最长255字")
    private String reason;
}
