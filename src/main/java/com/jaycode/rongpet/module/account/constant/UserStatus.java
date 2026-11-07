package com.jaycode.rongpet.module.account.constant;

/**
 * 小程序用户状态常量，与 t_user.status 列取值保持一致。
 */
public final class UserStatus {

    /** 正常 */
    public static final String NORMAL = "NORMAL";
    /** 限制发帖 */
    public static final String LIMIT_POST = "LIMIT_POST";
    /** 限制交易 */
    public static final String LIMIT_TRADE = "LIMIT_TRADE";
    /** 封禁：禁止登录 */
    public static final String BANNED = "BANNED";
    /** 冻结：禁止登录，可由运营解冻 */
    public static final String FROZEN = "FROZEN";
    /** 已注销：不可登录，数据按合规要求脱敏留存 */
    public static final String CANCELLED = "CANCELLED";

    private UserStatus() {
    }
}
