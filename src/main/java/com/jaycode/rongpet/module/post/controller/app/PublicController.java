package com.jaycode.rongpet.module.post.controller.app;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.feeder.dto.FeederCardVO;
import com.jaycode.rongpet.module.feeder.service.FeederService;
import com.jaycode.rongpet.module.post.dto.FoundVO;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import com.jaycode.rongpet.module.post.dto.PostDetailVO;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开浏览接口：无需登录即可查看帖子、已找回、喂养员、公告与协议。
 * 单独放在 /app/v1/public/**，与需要登录的私有接口隔离，避免鉴权排除误放行。
 */
@Tag(name = "App-公开浏览")
@RestController
@RequestMapping("/app/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final PostService postService;
    private final FeederService feederService;
    private final SysConfigService sysConfigService;

    @Operation(summary = "帖子列表", description = "sort=latest/reward，置顶优先")
    @GetMapping("/posts")
    public R<PageResult<PostCardVO>> posts(@RequestParam(required = false) String type,
                                           @RequestParam(required = false) String district,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(postService.list(type, district, sort, keyword, pageNum, pageSize));
    }

    @Operation(summary = "已找回列表", description = "按找回时间倒序，展示汇总分账口径")
    @GetMapping("/posts/found")
    public R<PageResult<FoundVO>> found(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(postService.foundList(pageNum, pageSize));
    }

    @Operation(summary = "帖子详情", description = "未登录也可查看，联系方式脱敏")
    @GetMapping("/posts/{postId}")
    public R<PostDetailVO> postDetail(@PathVariable long postId) {
        return R.ok(postService.detail(StpUserUtil.getLoginIdOrNull(), postId));
    }

    @Operation(summary = "喂养员列表")
    @GetMapping("/feeders")
    public R<PageResult<FeederCardVO>> feeders(@RequestParam(required = false) String district,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feederService.list(district, keyword, pageNum, pageSize));
    }

    @Operation(summary = "喂养员详情")
    @GetMapping("/feeders/{profileId}")
    public R<FeederCardVO> feederDetail(@PathVariable long profileId) {
        return R.ok(feederService.detail(profileId));
    }

    @Operation(summary = "首页公告")
    @GetMapping("/announcement")
    public R<Map<String, String>> announcement() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("text", sysConfigService.get("announcement.text", ""));
        return R.ok(result);
    }

    @Operation(summary = "协议文本", description = "type: REWARD/BREED/FEEDING/PRIVACY")
    @GetMapping("/agreements/{type}")
    public R<Map<String, String>> agreement(@PathVariable String type) {
        String key = type == null ? "" : type.toUpperCase();
        if (!key.matches("REWARD|BREED|FEEDING|PRIVACY")) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的协议类型");
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("type", key);
        result.put("text", sysConfigService.get("agreement." + key, ""));
        return R.ok(result);
    }
}
