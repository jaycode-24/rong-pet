package com.jaycode.rongpet.module.account.constant;

/**
 * 后台管理员状态常量，与 t_admin_user.status 列取值保持一致。
 */
public final class AdminStatus {

    /** 启用 */
    public static final String ENABLED = "ENABLED";
    /** 停用：禁止登录 */
    public static final String DISABLED = "DISABLED";

    private AdminStatus() {
    }
}
