package com.jaycode.rongpet.module.admin.service;

import com.jaycode.rongpet.module.admin.dto.StatsReportVO;

import java.nio.charset.StandardCharsets;

/**
 * 报表 CSV 导出工具。
 * 导出字段与页面明细行完全一致（PRD 11.3），带 UTF-8 BOM 保证 Excel 直接打开不乱码。
 */
public final class ReportCsvExporter {

    private ReportCsvExporter() {
    }

    public static byte[] toCsv(StatsReportVO vo) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，Excel 识别中文
        sb.append('\ufeff');
        sb.append("蓉宠帮运营报表-").append(periodName(vo.getPeriodType())).append('\n');
        sb.append("统计区间,").append(vo.getStartDate()).append(" 至 ").append(vo.getEndDate()).append('\n');
        sb.append("生成时间,").append(vo.getGeneratedAt()).append('\n');
        sb.append('\n');
        sb.append("指标,数值,单位,备注\n");
        for (StatsReportVO.Row row : vo.getRows()) {
            sb.append(escape(row.getMetric())).append(',')
                    .append(escape(row.getValue())).append(',')
                    .append(escape(row.getUnit())).append(',')
                    .append(escape(row.getRemark())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String fileName(StatsReportVO vo) {
        return "rongpet-report-" + vo.getPeriodType().toLowerCase()
                + "-" + vo.getStartDate() + "_" + vo.getEndDate() + ".csv";
    }

    private static String periodName(String type) {
        return switch (type) {
            case "WEEK" -> "周报";
            case "MONTH" -> "月报";
            default -> "日报";
        };
    }

    /** RFC 4180：含逗号、引号、换行的字段用双引号包裹并转义 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
