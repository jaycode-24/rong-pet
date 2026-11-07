package com.jaycode.rongpet.module.admin.controller;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.dto.StatsReportVO;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.admin.service.ReportCsvExporter;
import com.jaycode.rongpet.module.admin.service.StatsReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 运营报表（PRD 11.3）：日报/周报/月报，页面查看与 CSV 导出共用同一统计口径。
 * 注意：举报管理控制器同名，本类命名为 StatsReport 以区分。
 */
@Tag(name = "Admin-运营报表")
@RestController
@RequestMapping("/admin/v1/stats-reports")
@RequiredArgsConstructor
public class AdminStatsReportController {

    private final StatsReportService statsReportService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "报表查询", description = "periodType=DAY/WEEK/MONTH，date 为区间内任意日期，默认今天")
    @GetMapping
    public R<StatsReportVO> report(@RequestParam(defaultValue = "DAY") String periodType,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(statsReportService.report(periodType, date));
    }

    @Operation(summary = "报表导出", description = "CSV（UTF-8 BOM），导出字段与页面展示一致")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "DAY") String periodType,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        StatsReportVO vo = statsReportService.report(periodType, date);
        byte[] body = ReportCsvExporter.toCsv(vo);
        String fileName = URLEncoder.encode(ReportCsvExporter.fileName(vo), StandardCharsets.UTF_8)
                .replace("+", "%20");
        adminAuditService.record("REPORT_EXPORT", "REPORT", null,
                "type=" + vo.getPeriodType() + ", range=" + vo.getStartDate() + "~" + vo.getEndDate());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(body.length)
                .body(body);
    }
}
