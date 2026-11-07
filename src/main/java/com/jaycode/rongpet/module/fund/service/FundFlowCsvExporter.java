package com.jaycode.rongpet.module.fund.service;

import com.jaycode.rongpet.module.fund.entity.FundFlow;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 资金流水对账 CSV 导出（PRD FR-7.6 资金中心对账导出）。
 * 金额以分与元双列输出，UTF-8 BOM 保证 Excel 直接打开不乱码。
 */
public final class FundFlowCsvExporter {

    private FundFlowCsvExporter() {
    }

    public static byte[] toCsv(List<FundFlow> flows) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿');
        sb.append("流水号,用户ID,业务类型,业务单号,收支方向,金额(分),金额(元),状态,凭证号,操作人,备注,完成时间,创建时间\n");
        for (FundFlow f : flows) {
            sb.append(escape(f.getFlowNo())).append(',')
                    .append(f.getUserId() == null ? "" : f.getUserId()).append(',')
                    .append(escape(f.getBizType())).append(',')
                    .append(escape(f.getBizNo())).append(',')
                    .append(escape(f.getDirection())).append(',')
                    .append(f.getAmount() == null ? 0 : f.getAmount()).append(',')
                    .append(yuan(f.getAmount())).append(',')
                    .append(escape(f.getStatus())).append(',')
                    .append(escape(f.getVoucherNo())).append(',')
                    .append(escape(f.getOperator())).append(',')
                    .append(escape(f.getRemark())).append(',')
                    .append(f.getFinishTime() == null ? "" : f.getFinishTime()).append(',')
                    .append(f.getCreateTime() == null ? "" : f.getCreateTime()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String yuan(Long cents) {
        return String.format("%.2f", (cents == null ? 0L : cents) / 100.0);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
