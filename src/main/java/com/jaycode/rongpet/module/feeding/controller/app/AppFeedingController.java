package com.jaycode.rongpet.module.feeding.controller.app;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.common.dto.ReasonRequest;
import com.jaycode.rongpet.module.feeding.dto.FeedingDisputeCreateRequest;
import com.jaycode.rongpet.module.feeding.dto.FeedingDisputeVO;
import com.jaycode.rongpet.module.feeding.dto.FeedingCreateRequest;
import com.jaycode.rongpet.module.feeding.dto.FeedingVO;
import com.jaycode.rongpet.module.feeding.service.FeedingDisputeService;
import com.jaycode.rongpet.module.feeding.service.FeedingService;
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
 * 小程序上门喂养请求（纯信息模式）：发布、我的、喂养员工作台、标记联系/完成/关闭。
 */
@Tag(name = "App-喂养请求")
@RestController
@RequestMapping("/app/v1/feeding")
@RequiredArgsConstructor
public class AppFeedingController {

    private final FeedingService feedingService;
    private final FeedingDisputeService feedingDisputeService;

    @Operation(summary = "发布喂养请求", description = "定向喂养员或公开请求，须勾选服务须知")
    @PostMapping
    public R<FeedingVO> create(@Valid @RequestBody FeedingCreateRequest request) {
        return R.ok(feedingService.create(StpUserUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "我的喂养请求")
    @GetMapping("/mine")
    public R<PageResult<FeedingVO>> mine(@RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feedingService.mine(StpUserUtil.getLoginIdAsLong(), status, pageNum, pageSize));
    }

    @Operation(summary = "喂养员工作台", description = "定向请求 + 可服务区县的公开请求")
    @GetMapping("/feeder")
    public R<PageResult<FeedingVO>> feeder(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(feedingService.feederPage(StpUserUtil.getLoginIdAsLong(), status, pageNum, pageSize));
    }

    @Operation(summary = "喂养请求详情", description = "对接前隐藏详细地址并脱敏手机号")
    @GetMapping("/{feedingId}")
    public R<FeedingVO> detail(@PathVariable long feedingId) {
        return R.ok(feedingService.detail(StpUserUtil.getLoginIdAsLong(), feedingId));
    }

    @Operation(summary = "喂养员标记已联系", description = "公开请求被领取后双方可查看完整联系方式")
    @PostMapping("/{feedingId}/contact")
    public R<Void> contact(@PathVariable long feedingId) {
        feedingService.markContacted(StpUserUtil.getLoginIdAsLong(), feedingId);
        return R.ok();
    }

    @Operation(summary = "标记完成")
    @PostMapping("/{feedingId}/complete")
    public R<Void> complete(@PathVariable long feedingId) {
        feedingService.complete(StpUserUtil.getLoginIdAsLong(), feedingId);
        return R.ok();
    }

    @Operation(summary = "关闭请求")
    @PostMapping("/{feedingId}/close")
    public R<Void> close(@PathVariable long feedingId,
                         @RequestBody(required = false) ReasonRequest request) {
        feedingService.close(StpUserUtil.getLoginIdAsLong(), feedingId,
                request == null ? null : request.getReason());
        return R.ok();
    }

    @Operation(summary = "提交喂养纠纷凭证", description = "仅请求双方可提交，需上传凭证图片与描述")
    @PostMapping("/disputes")
    public R<FeedingDisputeVO> submitDispute(@Valid @RequestBody FeedingDisputeCreateRequest request) {
        return R.ok(feedingDisputeService.submit(StpUserUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "纠纷记录列表", description = "请求双方查看同一喂养请求下的全部纠纷与处理说明")
    @GetMapping("/{feedingId}/disputes")
    public R<List<FeedingDisputeVO>> disputes(@PathVariable long feedingId) {
        return R.ok(feedingDisputeService.listByFeeding(StpUserUtil.getLoginIdAsLong(), feedingId));
    }
}
