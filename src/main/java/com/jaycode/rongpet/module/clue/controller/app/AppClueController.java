package com.jaycode.rongpet.module.clue.controller.app;

import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.dto.ReasonRequest;
import com.jaycode.rongpet.module.clue.dto.ClueScoreRequest;
import com.jaycode.rongpet.module.clue.dto.ClueSubmitRequest;
import com.jaycode.rongpet.module.clue.dto.ClueVO;
import com.jaycode.rongpet.module.clue.service.ClueService;
import com.jaycode.rongpet.module.split.service.SplitService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序线索与评分：提交线索、发布者标记有效/无效、评分触发分账、选择公开明细。
 */
@Tag(name = "App-线索与评分")
@RestController
@RequestMapping("/app/v1/posts")
@RequiredArgsConstructor
public class AppClueController {

    private final ClueService clueService;
    private final SplitService splitService;

    @Operation(summary = "提交线索", description = "单帖单人最多3次，同一手机号视为作弊")
    @PostMapping("/{postId}/clues")
    public R<ClueVO> submit(@PathVariable long postId,
                            @Valid @RequestBody ClueSubmitRequest request) {
        return R.ok(clueService.submit(StpUserUtil.getLoginIdAsLong(), postId, request));
    }

    @Operation(summary = "线索列表", description = "发布者看全部，其他用户仅看自己的")
    @GetMapping("/{postId}/clues")
    public R<List<ClueVO>> list(@PathVariable long postId) {
        return R.ok(clueService.list(StpUserUtil.getLoginIdAsLong(), postId));
    }

    @Operation(summary = "标记线索有效", description = "单帖有效线索上限5条")
    @PostMapping("/{postId}/clues/{clueId}/valid")
    public R<Void> markValid(@PathVariable long postId, @PathVariable long clueId) {
        clueService.markValid(StpUserUtil.getLoginIdAsLong(), clueId);
        return R.ok();
    }

    @Operation(summary = "标记线索无效")
    @PostMapping("/{postId}/clues/{clueId}/invalid")
    public R<Void> markInvalid(@PathVariable long postId, @PathVariable long clueId,
                               @RequestBody(required = false) ReasonRequest request) {
        clueService.markInvalid(StpUserUtil.getLoginIdAsLong(), clueId,
                request == null ? null : request.getReason());
        return R.ok();
    }

    @Operation(summary = "提交线索评分", description = "对全部有效线索评1-5星，提交后生成分账方案")
    @PostMapping("/{postId}/scores")
    public R<Void> score(@PathVariable long postId,
                         @Valid @RequestBody ClueScoreRequest request) {
        splitService.submitScores(StpUserUtil.getLoginIdAsLong(), postId, request);
        return R.ok();
    }

    @Operation(summary = "设置分账明细是否公开", description = "仅线索提供者本人可操作")
    @PostMapping("/clues/{clueId}/public")
    public R<Void> publicDetail(@PathVariable long clueId,
                                @RequestParam(defaultValue = "true") boolean open) {
        splitService.toggleDetailPublic(StpUserUtil.getLoginIdAsLong(), clueId, open);
        return R.ok();
    }
}
