package com.jaycode.rongpet.module.experience;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.experience.dto.ExperienceRequests.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.jaycode.rongpet.security.StpUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Tag(name = "App-体验与反馈")
@RestController @RequestMapping("/app/v1") @RequiredArgsConstructor
public class AppExperienceController {
    private final ExperienceService service;
    @Operation(summary = "首页私人聚合")
 @GetMapping("/home/overview") public R<?> overview(){return R.ok(service.overview(StpUserUtil.getLoginIdAsLong()));}
    @Operation(summary = "收藏帖子")
 @PutMapping("/favorites/{postId}") public R<?> favorite(@PathVariable long postId){service.favorite(StpUserUtil.getLoginIdAsLong(),postId,true);return R.ok();}
    @Operation(summary = "取消收藏")
 @DeleteMapping("/favorites/{postId}") public R<?> unfavorite(@PathVariable long postId){service.favorite(StpUserUtil.getLoginIdAsLong(),postId,false);return R.ok();}
    @Operation(summary = "我的收藏")
 @GetMapping("/favorites") public R<?> favorites(@RequestParam(defaultValue="1") int page){return R.ok(service.favorites(StpUserUtil.getLoginIdAsLong(),page));}
    @Operation(summary = "创建申请")
 @PostMapping("/feedbacks") public R<?> create(@Valid @RequestBody Feedback r){return R.ok(Map.of("id",service.feedback(StpUserUtil.getLoginIdAsLong(),r.category().trim(),r.content().trim())));}
    @Operation(summary = "反馈列表")
 @GetMapping("/feedbacks") public R<?> feedbacks(@RequestParam(defaultValue="1") int page){return R.ok(service.feedbacks(StpUserUtil.getLoginIdAsLong(),page));}
    @Operation(summary = "本人反馈详情")
 @GetMapping("/feedbacks/{id}") public R<?> feedback(@PathVariable long id){return R.ok(service.feedbackDetail(StpUserUtil.getLoginIdAsLong(),id));}
    @Operation(summary = "读取宠物场景")
 @GetMapping("/pets/{id}/room") public R<?> room(@PathVariable long id){return R.ok(service.room(StpUserUtil.getLoginIdAsLong(),id));}
    @Operation(summary = "保存宠物场景")
 @PutMapping("/pets/{id}/room") public R<?> scene(@PathVariable long id,@Valid @RequestBody Scene r){service.scene(StpUserUtil.getLoginIdAsLong(),id,r.scene());return R.ok();}
}
