package com.jaycode.rongpet.module.experience;

import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.experience.entity.Conversation;
import com.jaycode.rongpet.module.experience.entity.ConversationMessage;
import com.jaycode.rongpet.module.experience.mapper.ConversationMapper;
import com.jaycode.rongpet.module.experience.mapper.ConversationMessageMapper;
import com.jaycode.rongpet.module.experience.mapper.ExperienceLockMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.security.SensitiveWordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationMapper conversations;
    @Mock private ConversationMessageMapper chatMessages;
    @Mock private ExperienceLockMapper locks;
    @Mock private MessageService messages;
    @Mock private SensitiveWordService words;
    @InjectMocks private ConversationService service;

    private Conversation conversation(long owner, long guest) {
        Conversation c = new Conversation();
        c.setId(900L);
        c.setPostId(10L);
        c.setOwnerId(owner);
        c.setGuestId(guest);
        return c;
    }

    @Test
    @DisplayName("正常消息落库并通知对方")
    void sendValidMessage() {
        when(conversations.selectById(900L)).thenReturn(conversation(1L, 2L));
        when(words.containsSensitive("约在小区门口见")).thenReturn(false);
        when(chatMessages.selectCount(any())).thenReturn(0L);

        service.send(2L, 900L, "约在小区门口见");

        verify(chatMessages).insert(any(ConversationMessage.class));
        verify(messages).send(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("含手机号/外链等导流内容直接拦截，不落库")
    void rejectContactDiversion() {
        when(conversations.selectById(900L)).thenReturn(conversation(1L, 2L));

        assertThatThrownBy(() -> service.send(2L, 900L, "加我微信 138 1234 5678"))
                .isInstanceOf(BizException.class);
        verify(chatMessages, never()).insert(any(ConversationMessage.class));
    }

    @Test
    @DisplayName("命中敏感词服务时拦截")
    void rejectSensitive() {
        when(conversations.selectById(900L)).thenReturn(conversation(1L, 2L));
        when(words.containsSensitive("违规内容")).thenReturn(true);

        assertThatThrownBy(() -> service.send(2L, 900L, "违规内容"))
                .isInstanceOf(BizException.class);
        verify(chatMessages, never()).insert(any(ConversationMessage.class));
    }

    @Test
    @DisplayName("一分钟内超过20条触发频控")
    void rateLimit() {
        when(conversations.selectById(900L)).thenReturn(conversation(1L, 2L));
        when(words.containsSensitive("普通消息")).thenReturn(false);
        when(chatMessages.selectCount(any())).thenReturn(20L);

        assertThatThrownBy(() -> service.send(2L, 900L, "普通消息"))
                .isInstanceOf(BizException.class);
        verify(chatMessages, never()).insert(any(ConversationMessage.class));
    }

    @Test
    @DisplayName("非会话参与方不能发消息")
    void outsiderCannotSend() {
        when(conversations.selectById(900L)).thenReturn(conversation(1L, 2L));

        assertThatThrownBy(() -> service.send(3L, 900L, "我是外人"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("不能给自己的帖子打开会话")
    void cannotOpenConversationWithSelf() {
        Post post = new Post();
        post.setId(10L);
        post.setUserId(7L);
        when(locks.lockPost(10L)).thenReturn(post);

        assertThatThrownBy(() -> service.open(7L, 10L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("并发首次打开命中唯一键时重查返回既有会话，保证幂等")
    void openIsIdempotentOnDuplicateKey() {
        Post post = new Post();
        post.setId(10L);
        post.setUserId(1L);
        when(locks.lockPost(10L)).thenReturn(post);
        when(conversations.selectOne(any())).thenReturn(null, conversation(1L, 2L));
        when(conversations.insert(any(Conversation.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("uk_conversation"));

        long id = service.open(2L, 10L);

        assertThat(id).isEqualTo(900L);
    }
}
