package com.jaycode.rongpet.module.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.module.report.entity.Report;
import com.jaycode.rongpet.module.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 举报服务：用户提交后进入管理后台待处理队列，24 小时内处理。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    public void create(long reporterId, String targetType, long targetId, String reason, String description) {
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDescription(description);
        report.setStatus("PENDING");
        reportMapper.insert(report);
    }

    public PageResult<Report> page(String status, int pageNum, int pageSize) {
        Page<Report> page = reportMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Report>()
                        .eq(status != null && !status.isBlank(), Report::getStatus, status)
                        .orderByAsc(Report::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public void handle(long reportId, long adminId, String status, String remark) {
        Report update = new Report();
        update.setId(reportId);
        update.setStatus(status);
        update.setHandleRemark(remark);
        update.setHandleAdminId(adminId);
        update.setHandleTime(java.time.LocalDateTime.now());
        reportMapper.updateById(update);
    }
}
