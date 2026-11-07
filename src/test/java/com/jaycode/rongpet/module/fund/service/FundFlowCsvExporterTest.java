package com.jaycode.rongpet.module.fund.service;

import com.jaycode.rongpet.module.fund.entity.FundFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundFlowCsvExporterTest {

    @Test
    @DisplayName("流水导出包含分/元双列与全部对账字段")
    void exportFlowWithYuanColumn() {
        FundFlow flow = new FundFlow();
        flow.setFlowNo("F123");
        flow.setUserId(1001L);
        flow.setBizType("REWARD");
        flow.setBizNo("2002");
        flow.setDirection("IN");
        flow.setAmount(12345L);
        flow.setStatus("SUCCESS");
        flow.setVoucherNo("WX2026");
        flow.setOperator("ADMIN-1");
        flow.setRemark("悬赏托管");

        String csv = new String(FundFlowCsvExporter.toCsv(List.of(flow)), StandardCharsets.UTF_8);

        assertThat(csv).contains("流水号,用户ID,业务类型");
        assertThat(csv).contains("F123,1001,REWARD,2002,IN,12345,123.45,SUCCESS,WX2026,ADMIN-1,悬赏托管");
    }

    @Test
    @DisplayName("空列表仅导出表头与 BOM")
    void emptyListOnlyHeader() {
        byte[] bytes = FundFlowCsvExporter.toCsv(List.of());
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("流水号");
    }
}
