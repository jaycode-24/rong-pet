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
import java.util.Map;
@Tag(name = "App-站内会话")
@RestController @RequestMapping("/app/v1/conversations") @RequiredArgsConstructor
public class ConversationController {
 private final ConversationService service;
 @Operation(summary = "创建或获取帖子会话")
 @PostMapping("/post/{postId}") public R<?> open(@PathVariable long postId){return R.ok(Map.of("id",service.open(StpUserUtil.getLoginIdAsLong(),postId)));}
 @Operation(summary = "分页列表")
 @GetMapping public R<?> list(@RequestParam(defaultValue="1") int page){return R.ok(service.list(StpUserUtil.getLoginIdAsLong(),page));}
 @Operation(summary = "会话历史，每页30条")
 @GetMapping("/{id}/messages") public R<?> history(@PathVariable long id,@RequestParam(defaultValue="1") int page){return R.ok(service.history(StpUserUtil.getLoginIdAsLong(),id,page));}
 @Operation(summary = "发送站内文字消息")
 @PostMapping("/{id}/messages") public R<?> send(@PathVariable long id,@Valid @RequestBody Text text){service.send(StpUserUtil.getLoginIdAsLong(),id,text.content().trim());return R.ok();}
}
