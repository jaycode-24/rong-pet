package com.jaycode.rongpet.module.message.controller.app;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.api.R;
import com.jaycode.rongpet.module.message.entity.Message;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.security.StpUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序消息中心：分页、未读数、单条已读、全部已读。
 */
@Tag(name = "App-站内消息")
@RestController
@RequestMapping("/app/v1/messages")
@RequiredArgsConstructor
public class AppMessageController {

    private final MessageService messageService;

    @Operation(summary = "消息分页")
    @GetMapping
    public R<PageResult<Message>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(messageService.page(StpUserUtil.getLoginIdAsLong(), pageNum, pageSize));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("count", messageService.unreadCount(StpUserUtil.getLoginIdAsLong()));
        return R.ok(result);
    }

    @Operation(summary = "标记单条已读")
    @PostMapping("/{messageId}/read")
    public R<Void> markRead(@PathVariable long messageId) {
        messageService.markRead(StpUserUtil.getLoginIdAsLong(), messageId);
        return R.ok();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public R<Void> markAllRead() {
        messageService.markAllRead(StpUserUtil.getLoginIdAsLong());
        return R.ok();
    }
}
