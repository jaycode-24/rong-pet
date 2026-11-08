package com.jaycode.rongpet.security;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpLogic;

/**
 * PC 运营后台管理员鉴权工具（Sa-Token 多账号体系，loginType=admin）。
 * 后台接口与小程序接口路径分离、鉴权分离，互不通用。
 */
public final class StpAdminUtil {

    public static final String TYPE = "admin";
    public static final StpLogic stpLogic = new StpLogic(TYPE);

    private StpAdminUtil() {
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

    public static void checkRole(String role) {
        stpLogic.checkRole(role);
    }

    public static long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    /** 未登录或处于无 Sa-Token 上下文的线程（启动任务、定时任务）时返回 null，不抛异常 */
    public static Long getLoginIdOrNull() {
        try {
            return stpLogic.isLogin() ? stpLogic.getLoginIdAsLong() : null;
        } catch (SaTokenException e) {
            return null;
        }
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }
}
