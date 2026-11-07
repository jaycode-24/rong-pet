package com.jaycode.rongpet.module.account.service;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.constant.AdminStatus;
import com.jaycode.rongpet.module.account.dto.AdminLoginRequest;
import com.jaycode.rongpet.module.account.dto.AdminLoginVO;
import com.jaycode.rongpet.module.account.dto.AdminUserVO;
import com.jaycode.rongpet.module.account.entity.AdminUser;
import com.jaycode.rongpet.module.account.entity.AdminLoginLog;
import com.jaycode.rongpet.module.account.error.AccountErrorCode;
import com.jaycode.rongpet.module.account.mapper.AdminLoginLogMapper;
import com.jaycode.rongpet.module.account.mapper.AdminUserMapper;
import com.jaycode.rongpet.security.StpAdminUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 后台管理员认证服务。
 * <ul>
 *   <li>密码校验：BCrypt 加盐哈希比对</li>
 *   <li>防爆破：Redis 失败计数（30 分钟滑动），连续 5 次置 lock_until 锁定 30 分钟</li>
 *   <li>审计：每次尝试均写入 t_admin_login_log（append-only）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final long MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final String FAIL_KEY_PREFIX = "rongpet:admin:login:fail:";

    private final AdminUserMapper adminUserMapper;
    private final AdminLoginLogMapper loginLogMapper;
    private final StringRedisTemplate redisTemplate;

    public AdminLoginVO login(AdminLoginRequest request, HttpServletRequest servletRequest) {
        AdminUser admin = selectByUsername(request.getUsername());
        try {
            doLoginCheck(request, admin);
        } catch (BizException e) {
            boolean countable = admin != null && e.getErrorCode() == AccountErrorCode.ADMIN_CREDENTIALS_INVALID;
            if (countable) {
                long fails = incrementFailCount(request.getUsername());
                if (fails >= MAX_FAIL_COUNT) {
                    lockAdmin(admin);
                    e = new BizException(AccountErrorCode.ADMIN_LOGIN_LOCKED);
                } else {
                    log.debug("管理员登录失败 username={}, fails={}/{}", request.getUsername(), fails, MAX_FAIL_COUNT);
                }
            }
            insertLoginLog(admin, request.getUsername(), servletRequest, 0, e.getMessage());
            throw e;
        }

        insertLoginLog(admin, request.getUsername(), servletRequest, 1, null);
        redisTemplate.delete(failKey(request.getUsername()));

        AdminUser touch = new AdminUser();
        touch.setId(admin.getId());
        touch.setLockUntil(null);
        touch.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(touch);

        StpAdminUtil.login(admin.getId());
        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(StpAdminUtil.getTokenValue());
        vo.setAdmin(toVO(loadAdmin(admin.getId())));
        log.info("管理员登录成功 adminId={}, username={}", admin.getId(), admin.getUsername());
        return vo;
    }

    public AdminUserVO me() {
        return toVO(loadAdmin(StpAdminUtil.getLoginIdAsLong()));
    }

    public void logout() {
        StpAdminUtil.logout();
    }

    /** 密码错误抛 ADMIN_CREDENTIALS_INVALID 并累计；锁定/停用直接拒绝且不累计 */
    private void doLoginCheck(AdminLoginRequest request, AdminUser admin) {
        if (admin == null) {
            throw new BizException(AccountErrorCode.ADMIN_CREDENTIALS_INVALID);
        }
        if (AdminStatus.DISABLED.equals(admin.getStatus())) {
            throw new BizException(AccountErrorCode.ADMIN_DISABLED);
        }
        if (admin.getLockUntil() != null && admin.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BizException(AccountErrorCode.ADMIN_LOGIN_LOCKED);
        }
        if (!BCrypt.checkpw(request.getPassword(), admin.getPassword())) {
            throw new BizException(AccountErrorCode.ADMIN_CREDENTIALS_INVALID);
        }
    }

    private long incrementFailCount(String username) {
        String key = failKey(username);
        Long fails = redisTemplate.opsForValue().increment(key);
        if (fails != null && fails == 1) {
            redisTemplate.expire(key, LOCK_DURATION);
        }
        return fails == null ? 0 : fails;
    }

    private void lockAdmin(AdminUser admin) {
        AdminUser update = new AdminUser();
        update.setId(admin.getId());
        update.setLockUntil(LocalDateTime.now().plus(LOCK_DURATION));
        adminUserMapper.updateById(update);
        log.warn("管理员连续密码错误已达上限，账户锁定 username={}", admin.getUsername());
    }

    private void insertLoginLog(AdminUser admin, String username,
                                HttpServletRequest servletRequest, int result, String failReason) {
        AdminLoginLog logEntry = new AdminLoginLog();
        logEntry.setAdminId(admin == null ? null : admin.getId());
        logEntry.setUsername(username);
        logEntry.setLoginIp(clientIp(servletRequest));
        logEntry.setUserAgent(truncate(servletRequest.getHeader("User-Agent"), 255));
        logEntry.setLoginResult(result);
        logEntry.setFailReason(truncate(failReason, 255));
        loginLogMapper.insert(logEntry);
    }

    private AdminUser loadAdmin(Long id) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BizException(AccountErrorCode.ADMIN_NOT_FOUND);
        }
        return admin;
    }

    private AdminUser selectByUsername(String username) {
        return adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
    }

    private AdminUserVO toVO(AdminUser admin) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setRole(admin.getRole());
        vo.setStatus(admin.getStatus());
        vo.setLastLoginTime(admin.getLastLoginTime());
        return vo;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String failKey(String username) {
        return FAIL_KEY_PREFIX + username;
    }
}
