package com.jaycode.rongpet.module.post.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.post.entity.TopRecord;
import com.jaycode.rongpet.module.post.service.TopService;
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
 * 后台置顶管理：免费置顶截图核验、人工置顶与记录查询。
 */
@Tag(name = "Admin-置顶")
@RestController
@RequestMapping("/admin/v1/tops")
@RequiredArgsConstructor
public class AdminTopController {

    private final TopService topService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "置顶记录分页")
    @GetMapping
    public R<PageResult<TopRecord>> page(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(topService.adminPage(type, status, pageNum, pageSize));
    }

    @Operation(summary = "通过免费置顶申请")
    @PostMapping("/free/{recordId}/approve")
    public R<Void> approveFree(@PathVariable long recordId) {
        topService.approveFree(StpAdminUtil.getLoginIdAsLong(), recordId);
        adminAuditService.record("TOP_FREE_APPROVE", "TOP", recordId, null);
        return R.ok();
    }

    @Operation(summary = "驳回免费置顶申请")
    @PostMapping("/free/{recordId}/reject")
    public R<Void> rejectFree(@PathVariable long recordId,
                              @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写驳回原因");
        }
        topService.rejectFree(StpAdminUtil.getLoginIdAsLong(), recordId, reason);
        adminAuditService.record("TOP_FREE_REJECT", "TOP", recordId, reason);
        return R.ok();
    }

    @Operation(summary = "人工置顶", description = "targetId 为帖子ID，days 最长3天")
    @PostMapping("/manual")
    public R<Void> manual(@RequestBody AdminActionRequest request) {
        if (request.getTargetId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请指定帖子ID");
        }
        int days = request.getDays() == null ? 1 : request.getDays();
        topService.manualTop(StpAdminUtil.getLoginIdAsLong(), request.getTargetId(), days, request.getReason());
        adminAuditService.record("TOP_MANUAL", "POST", request.getTargetId(),
                "days=" + days + (StringUtils.hasText(request.getReason()) ? ", reason=" + request.getReason() : ""));
        return R.ok();
    }
}
