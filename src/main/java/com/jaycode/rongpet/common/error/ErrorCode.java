package com.jaycode.rongpet.common.error;

/**
 * 业务错误码契约。各模块自定义枚举实现本接口，编码按模块前缀划分（如 POST_、PAY_、CLUE_）。
 */
public interface ErrorCode {

    /** 机器可读错误码 */
    String getCode();

    /** 面向用户的提示文案，集中维护，禁止散落在 Controller 中硬编码 */
    String getMessage();
}
