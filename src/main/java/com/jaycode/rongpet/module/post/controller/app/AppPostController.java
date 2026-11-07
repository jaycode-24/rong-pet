package com.jaycode.rongpet.module.post.controller.app;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.post.dto.FoundConfirmRequest;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import com.jaycode.rongpet.module.post.dto.PostCreateRequest;
import com.jaycode.rongpet.module.post.dto.PostDetailVO;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 小程序帖子接口：发布、编辑、我的、详情、下架、延长、确认找回与疑似匹配。
 * 公开浏览请走 /app/v1/public/posts。
 */
@Tag(name = "App-帖子")
@RestController
@RequestMapping("/app/v1/posts")
@RequiredArgsConstructor
public class AppPostController {

    private final PostService postService;

    @Operation(summary = "发布帖子", description = "draft=true 时保存草稿")
    @PostMapping
    public R<PostDetailVO> create(@Valid @RequestBody PostCreateRequest request) {
        return R.ok(postService.create(StpUserUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "编辑帖子", description = "展示中帖子仅可编辑一次，编辑后重新审核")
    @PutMapping("/{postId}")
    public R<PostDetailVO> update(@PathVariable long postId,
                                  @Valid @RequestBody PostCreateRequest request) {
        return R.ok(postService.update(StpUserUtil.getLoginIdAsLong(), postId, request));
    }

    @Operation(summary = "我的帖子", description = "group: showing/audit/ended/rejected")
    @GetMapping("/mine")
    public R<PageResult<PostCardVO>> mine(@RequestParam(required = false) String group,
                                          @RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(postService.mine(StpUserUtil.getLoginIdAsLong(), group, pageNum, pageSize));
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/{postId}")
    public R<PostDetailVO> detail(@PathVariable long postId) {
        return R.ok(postService.detail(StpUserUtil.getLoginIdAsLong(), postId));
    }

    @Operation(summary = "下架帖子")
    @PostMapping("/{postId}/take-down")
    public R<Void> takeDown(@PathVariable long postId) {
        postService.takeDown(StpUserUtil.getLoginIdAsLong(), postId);
        return R.ok();
    }

    @Operation(summary = "延长有效期", description = "每帖仅可延长一次")
    @PostMapping("/{postId}/extend")
    public R<Void> extend(@PathVariable long postId) {
        postService.extend(StpUserUtil.getLoginIdAsLong(), postId);
        return R.ok();
    }

    @Operation(summary = "确认找回", description = "上传归还凭证，有悬赏时进入待评分")
    @PostMapping("/{postId}/found")
    public R<Void> confirmFound(@PathVariable long postId,
                                @Valid @RequestBody FoundConfirmRequest request) {
        postService.confirmFound(StpUserUtil.getLoginIdAsLong(), postId, request);
        return R.ok();
    }

    @Operation(summary = "疑似对端匹配", description = "品种 + 区县 + 时间窗前后7天，返回最多5条")
    @GetMapping("/match")
    public R<List<PostCardVO>> match(@RequestParam String type,
                                     @RequestParam(required = false) String petType,
                                     @RequestParam(required = false) String breed,
                                     @RequestParam(required = false) String district,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime eventTime) {
        return R.ok(postService.match(type, petType, breed, district, eventTime));
    }
}
