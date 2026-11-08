package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.*;
import com.jaycode.rongpet.module.experience.entity.Conversation;
import com.jaycode.rongpet.module.experience.entity.ConversationMessage;
import com.jaycode.rongpet.module.experience.mapper.*;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.security.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 站内沟通会话服务，承载发帖人与访客围绕帖子的一对一沟通。
 * 会话按 (帖子, 访客) 唯一并幂等创建；消息发送经过联系方式/外链/私下收款拦截、敏感词过滤和频控，
 * 入库后给接收方发系统通知，引导双方留在平台内沟通。
 */
@Service
@RequiredArgsConstructor
public class ConversationService {
    /** 历史消息分页大小 */
    public static final int HISTORY_PAGE_SIZE = 30;
    /** 单用户每分钟最多发送消息数，超出提示发送频繁 */
    private static final int MAX_MESSAGES_PER_MINUTE = 20;
    private static final Pattern CONTACT = Pattern.compile("(?i)(1[3-9][\\s-]*[0-9][0-9\\s-]{8,}|https?://|微信|加微|加我|wx[:：]|转账|收款码|支付码)");
    private final ConversationMapper conversations;
    private final ConversationMessageMapper chatMessages;
    private final ExperienceLockMapper locks;
    private final MessageService messages;
    private final SensitiveWordService words;

    @Transactional
    public long open(long user, long postId) {
        Post post = locks.lockPost(postId);
        if (post == null) throw new BizException(CommonErrorCode.NOT_FOUND, "帖子不存在");
        if (Objects.equals(post.getUserId(), user)) throw new BizException(CommonErrorCode.BAD_REQUEST, "不能和自己创建会话");
        Conversation old = conversations.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPostId, postId).eq(Conversation::getGuestId, user));
        if (old != null) return old.getId();
        Conversation item = new Conversation();
        item.setPostId(postId);
        item.setOwnerId(post.getUserId());
        item.setGuestId(user);
        try {
            conversations.insert(item);
        } catch (DuplicateKeyException error) {
            // 并发首次打开命中 uk_conversation 唯一键，重查返回既有会话，保证幂等而不是抛500。
            Conversation existing = conversations.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getPostId, postId).eq(Conversation::getGuestId, user));
            if (existing != null) return existing.getId();
            throw error;
        }
        return item.getId();
    }

    public List<ConversationItem> list(long user, int page) {
        ExperienceService.validatePage(page);
        return conversations.selectPage(new Page<>(page, ExperienceService.PAGE_SIZE, false),
                new LambdaQueryWrapper<Conversation>().and(w -> w.eq(Conversation::getOwnerId, user).or().eq(Conversation::getGuestId, user))
                        .orderByDesc(Conversation::getCreateTime).orderByDesc(Conversation::getId)).getRecords().stream()
                .map(c -> new ConversationItem(c.getId(), c.getPostId(), c.getCreateTime())).toList();
    }

    private Conversation require(long user, long id) {
        Conversation conversation = conversations.selectById(id);
        if (conversation == null || !Objects.equals(conversation.getOwnerId(), user) && !Objects.equals(conversation.getGuestId(), user))
            throw new BizException(CommonErrorCode.NOT_FOUND, "会话不存在");
        return conversation;
    }

    public List<ChatMessage> history(long user, long id, int page) {
        require(user, id);
        ExperienceService.validatePage(page);
        return chatMessages.selectPage(new Page<>(page, HISTORY_PAGE_SIZE, false),
                new LambdaQueryWrapper<ConversationMessage>().eq(ConversationMessage::getConversationId, id)
                        .orderByDesc(ConversationMessage::getId)).getRecords().stream()
                .map(m -> new ChatMessage(m.getId(), m.getSenderId(), m.getContent(), m.getCreateTime())).toList();
    }

    @Transactional
    public void send(long user, long id, String content) {
        Conversation conversation = require(user, id);
        if (CONTACT.matcher(content).find() || words.containsSensitive(content))
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请在平台沟通，勿发送联系方式、外链或私下收付款信息");
        long recent = chatMessages.selectCount(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getSenderId, user).gt(ConversationMessage::getCreateTime, LocalDateTime.now().minusMinutes(1)));
        if (recent >= MAX_MESSAGES_PER_MINUTE) throw new BizException(CommonErrorCode.BAD_REQUEST, "发送过于频繁，请稍后重试");
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(id);
        message.setSenderId(user);
        message.setContent(content);
        chatMessages.insert(message);
        long recipient = Objects.equals(conversation.getOwnerId(), user) ? conversation.getGuestId() : conversation.getOwnerId();
        messages.send(recipient, MessageService.TYPE_SYSTEM, "收到站内消息", "你有一条新的沟通消息", "CONVERSATION", String.valueOf(id));
    }
}
