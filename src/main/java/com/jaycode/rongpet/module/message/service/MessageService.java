package com.jaycode.rongpet.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.module.message.entity.Message;
import com.jaycode.rongpet.module.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息服务。微信订阅消息作为渠道补充（模板 ID 就绪后接入），站内消息为兜底与消息中心数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String TYPE_SYSTEM = "SYSTEM";
    public static final String TYPE_AUDIT = "AUDIT";
    public static final String TYPE_ORDER = "ORDER";
    public static final String TYPE_FUND = "FUND";

    private final MessageMapper messageMapper;

    public void send(Long userId, String type, String title, String content, String bizType, String bizNo) {
        if (userId == null) {
            return;
        }
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizNo(bizNo);
        message.setIsRead(0);
        messageMapper.insert(message);
        log.info("站内消息已发送 userId={}, type={}, title={}, biz={}/{}", userId, type, title, bizType, bizNo);
    }

    public PageResult<Message> page(long userId, int pageNum, int pageSize) {
        Page<Message> page = messageMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByDesc(Message::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public long unreadCount(long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
    }

    public void markRead(long userId, long messageId) {
        Message update = new Message();
        update.setId(messageId);
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
    }

    public void markAllRead(long userId) {
        Message update = new Message();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0));
    }

    /** 供后台检索的最近消息，便于辅助排查 */
    public List<Message> recent(long userId, int limit) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .orderByDesc(Message::getCreateTime)
                .last("limit " + limit));
    }
}
