package com.jaycode.rongpet.module.account.config;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.module.account.constant.AdminRole;
import com.jaycode.rongpet.module.account.constant.AdminStatus;
import com.jaycode.rongpet.module.account.entity.AdminUser;
import com.jaycode.rongpet.module.account.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * dev 环境管理员初始化：账号表为空时创建默认管理员账号，密码取配置，仅本地联调用。
 * 生产环境一律通过 SQL 变更流程人工创建首账号，本组件不生效（@Profile("dev")）。
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminInitializer implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;

    @Value("${rongpet.dev.admin-username:admin}")
    private String username;

    @Value("${rongpet.dev.admin-password:Admin@123456}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        Long count = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUser>());
        if (count != null && count > 0) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(BCrypt.hashpw(password));
        admin.setRealName("开发管理员");
        admin.setRole(AdminRole.SUPER);
        admin.setStatus(AdminStatus.ENABLED);
        admin.setLastLoginTime(null);
        adminUserMapper.insert(admin);
        log.warn("已创建 dev 管理员账号 username={}（密码见 rongpet.dev.admin-password，仅限本地）", username);
    }
}
