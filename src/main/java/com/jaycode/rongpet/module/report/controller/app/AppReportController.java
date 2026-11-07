package com.jaycode.rongpet.module.report.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.report.dto.ReportCreateRequest;
import com.jaycode.rongpet.module.report.service.ReportService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序举报入口：帖子、线索、用户、喂养请求均可举报，进入后台待处理队列。
 */
@Tag(name = "App-举报")
@RestController
@RequestMapping("/app/v1/reports")
@RequiredArgsConstructor
public class AppReportController {

    private final ReportService reportService;

    @Operation(summary = "提交举报", description = "targetType: POST/CLUE/USER/FEEDING")
    @PostMapping
    public R<Void> create(@Valid @RequestBody ReportCreateRequest request) {
        reportService.create(StpUserUtil.getLoginIdAsLong(), request.getTargetType(),
                request.getTargetId(), request.getReason(), request.getDescription());
        return R.ok();
    }
}
