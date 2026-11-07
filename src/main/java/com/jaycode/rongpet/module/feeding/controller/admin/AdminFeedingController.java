package com.jaycode.rongpet.module.feeding.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.feeding.dto.FeedingDisputeVO;
import com.jaycode.rongpet.module.feeding.dto.FeedingVO;
import com.jaycode.rongpet.module.feeding.service.FeedingDisputeService;
import com.jaycode.rongpet.module.feeding.service.FeedingService;
import com.jaycode.rongpet.security.StpAdminUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台喂养请求管理：全程信息对接，无资金操作。
 */
@Tag(name = "Admin-喂养请求")
@RestController
@RequestMapping("/admin/v1/feeding")
@RequiredArgsConstructor
public class AdminFeedingController {

    private final FeedingService feedingService;
    private final FeedingDisputeService feedingDisputeService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "喂养请求分页")
    @GetMapping
    public R<PageResult<FeedingVO>> page(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) Long feederId,
                                         @RequestParam(required = false) Long feedingId,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feedingService.adminPage(status, userId, feederId, feedingId, pageNum, pageSize));
    }

    @Operation(summary = "喂养请求详情")
    @GetMapping("/{feedingId}")
    public R<FeedingVO> detail(@PathVariable long feedingId) {
        return R.ok(feedingService.adminDetail(feedingId));
    }

    @Operation(summary = "平台关闭请求")
    @PostMapping("/{feedingId}/close")
    public R<Void> close(@PathVariable long feedingId,
                         @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.getReason();
        feedingService.adminClose(feedingId, reason);
        adminAuditService.record("FEEDING_CLOSE", "FEEDING", feedingId, reason);
        return R.ok();
    }

    @Operation(summary = "喂养纠纷分页", description = "查看双方提交的凭证与沟通材料，按状态/请求编号检索")
    @GetMapping("/disputes")
    public R<PageResult<FeedingDisputeVO>> disputes(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long feedingId,
                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feedingDisputeService.adminPage(status, feedingId, pageNum, pageSize));
    }

    @Operation(summary = "纠纷处理归档", description = "填写处理说明并归档，结论通知请求双方")
    @PostMapping("/disputes/{disputeId}/archive")
    public R<Void> archiveDispute(@PathVariable long disputeId,
                                  @RequestBody(required = false) AdminActionRequest request) {
        String remark = request == null ? null : request.getReason();
        if (!StringUtils.hasText(remark)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写处理说明");
        }
        feedingDisputeService.archive(StpAdminUtil.getLoginIdAsLong(), disputeId, remark);
        adminAuditService.record("FEEDING_DISPUTE_ARCHIVE", "FEEDING_DISPUTE", disputeId, remark);
        return R.ok();
    }
}
