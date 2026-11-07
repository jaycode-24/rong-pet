package com.jaycode.rongpet.module.fund.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.fund.entity.FundFlow;
import com.jaycode.rongpet.module.fund.service.FundFlowCsvExporter;
import com.jaycode.rongpet.module.fund.service.FundFlowService;
import com.jaycode.rongpet.security.StpAdminUtil;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台资金中心（PRD FR-7.6）：流水查询与对账导出。资金敏感操作仅超级管理员。
 */
@Tag(name = "Admin-资金流水")
@RestController
@RequestMapping("/admin/v1/fund-flows")
@RequiredArgsConstructor
public class AdminFundFlowController {

    private final FundFlowService fundFlowService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "资金流水分页")
    @GetMapping
    public R<PageResult<FundFlow>> page(@RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) String bizType,
                                        @RequestParam(required = false) String direction,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(fundFlowService.page(userId, bizType, direction, pageNum, pageSize));
    }

    @Operation(summary = "对账导出", description = "按时间区间导出 CSV（UTF-8 BOM），仅超级管理员")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String bizType) {
        StpAdminUtil.checkRole("SUPER");
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
        List<FundFlow> flows = fundFlowService.exportRange(fromTime, toTime, bizType);
        byte[] body = FundFlowCsvExporter.toCsv(flows);
        String rawName = "rongpet-fund-flow-" + (from == null ? "all" : from) + "_" + (to == null ? "now" : to) + ".csv";
        String fileName = URLEncoder.encode(rawName, StandardCharsets.UTF_8).replace("+", "%20");
        adminAuditService.record("FUND_FLOW_EXPORT", "FUND_FLOW", null,
                "from=" + from + ", to=" + to + ", bizType=" + bizType + ", rows=" + flows.size());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(body.length)
                .body(body);
    }
}
