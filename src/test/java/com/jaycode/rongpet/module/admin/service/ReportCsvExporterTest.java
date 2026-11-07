package com.jaycode.rongpet.module.admin.service;

import com.jaycode.rongpet.module.admin.dto.StatsReportVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCsvExporterTest {

    @Test
    @DisplayName("CSV 含 UTF-8 BOM、表头与明细行，逗号字段被双引号包裹")
    void exportContainsBomHeaderAndRows() {
        StatsReportVO vo = new StatsReportVO();
        vo.setPeriodType("DAY");
        vo.setStartDate(LocalDate.of(2026, 11, 7));
        vo.setEndDate(LocalDate.of(2026, 11, 7));
        vo.setGeneratedAt("2026-11-07 10:00:00");
        vo.setRows(List.of(
                new StatsReportVO.Row("新增用户", "12", "人", ""),
                new StatsReportVO.Row("确认收入", "88.00", "元", "悬赏服务费,置顶费")));

        byte[] bytes = ReportCsvExporter.toCsv(vo);
        String csv = new String(bytes, StandardCharsets.UTF_8);

        // BOM
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(csv).contains("指标,数值,单位,备注");
        assertThat(csv).contains("新增用户,12,人,");
        // 含逗号的备注按 RFC4180 转义
        assertThat(csv).contains("\"悬赏服务费,置顶费\"");
        assertThat(ReportCsvExporter.fileName(vo)).startsWith("rongpet-report-day-");
    }

    @Test
    @DisplayName("空明细仍输出表头")
    void emptyReportKeepsHeader() {
        StatsReportVO vo = new StatsReportVO();
        vo.setPeriodType("MONTH");
        vo.setStartDate(LocalDate.of(2026, 11, 1));
        vo.setEndDate(LocalDate.of(2026, 11, 30));
        vo.setGeneratedAt("2026-11-30 23:59:59");
        vo.setRows(List.of());

        String csv = new String(ReportCsvExporter.toCsv(vo), StandardCharsets.UTF_8);
        assertThat(csv).contains("月报");
        assertThat(csv).contains("指标,数值,单位,备注");
    }
}
