package com.jaycode.rongpet.security;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 小程序端用户鉴权工具（Sa-Token 多账号体系，loginType=user）。
 * 与管理员端 {@link StpAdminUtil} 会话完全隔离，token 存 Redis。
 */
public final class StpUserUtil {

    public static final String TYPE = "user";
    public static final StpLogic stpLogic = new StpLogic(TYPE);

    private StpUserUtil() {
    }

    public static void login(Object id) {
        stpLogic.login(id);
    }

    public static void logout() {
        stpLogic.logout();
    }

    public static boolean isLogin() {
        return stpLogic.isLogin();
    }

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    public static long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    /** 未登录返回 null，不抛异常，用于审计字段填充等内部场景 */
    public static Long getLoginIdOrNull() {
        return stpLogic.isLogin() ? stpLogic.getLoginIdAsLong() : null;
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }
}
