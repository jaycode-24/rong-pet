package com.jaycode.rongpet.module.post.controller.admin;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.admin.dto.AdminActionRequest;
import com.jaycode.rongpet.module.admin.service.AdminAuditService;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import com.jaycode.rongpet.module.post.dto.PostDetailVO;
import com.jaycode.rongpet.module.post.entity.PostLog;
import com.jaycode.rongpet.module.post.service.PostService;
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

import java.util.List;

/**
 * 后台帖子管理：审核、下架、详情与操作日志。
 */
@Tag(name = "Admin-帖子")
@RestController
@RequestMapping("/admin/v1/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;
    private final AdminAuditService adminAuditService;

    @Operation(summary = "帖子分页")
    @GetMapping
    public R<PageResult<PostCardVO>> page(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(postService.adminPage(status, type, keyword, pageNum, pageSize));
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/{postId}")
    public R<PostDetailVO> detail(@PathVariable long postId) {
        return R.ok(postService.adminDetail(postId));
    }

    @Operation(summary = "帖子操作日志")
    @GetMapping("/{postId}/logs")
    public R<List<PostLog>> logs(@PathVariable long postId) {
        return R.ok(postService.logs(postId));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{postId}/approve")
    public R<Void> approve(@PathVariable long postId,
                           @RequestBody(required = false) AdminActionRequest request) {
        String remark = request == null ? null : request.getReason();
        postService.approve(StpAdminUtil.getLoginIdAsLong(), postId, remark);
        adminAuditService.record("POST_APPROVE", "POST", postId, remark);
        return R.ok();
    }

    @Operation(summary = "审核驳回")
    @PostMapping("/{postId}/reject")
    public R<Void> reject(@PathVariable long postId,
                          @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写驳回原因");
        }
        postService.reject(StpAdminUtil.getLoginIdAsLong(), postId, reason);
        adminAuditService.record("POST_REJECT", "POST", postId, reason);
        return R.ok();
    }

    @Operation(summary = "平台下架")
    @PostMapping("/{postId}/take-down")
    public R<Void> takeDown(@PathVariable long postId,
                            @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写下架原因");
        }
        postService.adminTakeDown(StpAdminUtil.getLoginIdAsLong(), postId, reason);
        adminAuditService.record("POST_TAKE_DOWN", "POST", postId, reason);
        return R.ok();
    }

    @Operation(summary = "隐藏已找回内容", description = "争议期间从已找回列表隐藏，需填写理由并通知相关方")
    @PostMapping("/{postId}/found/hide")
    public R<Void> hideFound(@PathVariable long postId,
                             @RequestBody(required = false) AdminActionRequest request) {
        String reason = request == null ? null : request.getReason();
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写隐藏理由");
        }
        postService.hideFound(StpAdminUtil.getLoginIdAsLong(), postId, reason);
        adminAuditService.record("FOUND_HIDE", "POST", postId, reason);
        return R.ok();
    }

    @Operation(summary = "恢复已找回内容")
    @PostMapping("/{postId}/found/restore")
    public R<Void> restoreFound(@PathVariable long postId) {
        postService.restoreFound(StpAdminUtil.getLoginIdAsLong(), postId);
        adminAuditService.record("FOUND_RESTORE", "POST", postId, null);
        return R.ok();
    }

    @Operation(summary = "标注已找回内容", description = "记录争议说明或处理结论")
    @PostMapping("/{postId}/found/mark")
    public R<Void> markFound(@PathVariable long postId,
                             @RequestBody(required = false) AdminActionRequest request) {
        String mark = request == null ? null : request.getReason();
        if (!StringUtils.hasText(mark)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写标注内容");
        }
        postService.markFound(StpAdminUtil.getLoginIdAsLong(), postId, mark);
        adminAuditService.record("FOUND_MARK", "POST", postId, mark);
        return R.ok();
    }
}
