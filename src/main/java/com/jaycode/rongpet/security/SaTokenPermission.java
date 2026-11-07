package com.jaycode.rongpet.security;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.module.account.constant.AdminStatus;
import com.jaycode.rongpet.module.account.entity.AdminUser;
import com.jaycode.rongpet.module.account.mapper.AdminUserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 权限数据供给（多账号体系按 loginType 区分）。
 * 管理员角色实时读取数据库，停用/删除账号立即失效；用户端 V1 不使用角色权限，恒为空。
 */
@Component
@RequiredArgsConstructor
public class SaTokenPermission implements StpInterface {

    private final AdminUserMapper adminUserMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (StpAdminUtil.TYPE.equals(loginType)) {
            AdminUser admin = selectSafely(parseId(loginId));
            if (admin != null && AdminStatus.ENABLED.equals(admin.getStatus()) && admin.getRole() != null) {
                return List.of(admin.getRole());
            }
        }
        return List.of();
    }

    /** 管理员已删除时 selectById 命中逻辑删除过滤返回 null，此时角色为空即可阻断越权 */
    private AdminUser selectSafely(Long id) {
        return adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getId, id));
    }

    private Long parseId(Object loginId) {
        try {
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
