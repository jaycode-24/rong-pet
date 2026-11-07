package com.jaycode.rongpet.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.module.account.entity.AdminUser;
import com.jaycode.rongpet.module.account.mapper.AdminUserMapper;
import com.jaycode.rongpet.module.admin.entity.AdminAuditLog;
import com.jaycode.rongpet.module.admin.mapper.AdminAuditLogMapper;
import com.jaycode.rongpet.security.StpAdminUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 后台审计日志服务：所有管理端写操作必须调用，自动记录操作人、IP 与目标对象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogMapper auditLogMapper;
    private final AdminUserMapper adminUserMapper;

    public void record(String action, String targetType, Long targetId, String detail) {
        Long adminId = StpAdminUtil.getLoginIdOrNull();
        if (adminId == null) {
            return;
        }
        AdminUser admin = adminUserMapper.selectById(adminId);
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminId(adminId);
        entry.setUsername(admin == null ? String.valueOf(adminId) : admin.getUsername());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(truncate(detail, 1000));
        entry.setIp(currentIp());
        auditLogMapper.insert(entry);
        log.info("后台审计 admin={}, action={}, target={}/{}", entry.getUsername(), action, targetType, targetId);
    }

    public PageResult<AdminAuditLog> page(Long adminId, String action, int pageNum, int pageSize) {
        Page<AdminAuditLog> page = auditLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminAuditLog>()
                        .eq(adminId != null, AdminAuditLog::getAdminId, adminId)
                        .eq(StringUtils.hasText(action), AdminAuditLog::getAction, action)
                        .orderByDesc(AdminAuditLog::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return truncate(forwarded.split(",")[0].trim(), 64);
            }
            return truncate(request.getRemoteAddr(), 64);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
