package com.jaycode.rongpet.module.experience;

import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.experience.entity.RewardDispute;
import com.jaycode.rongpet.module.experience.mapper.ExperienceLockMapper;
import com.jaycode.rongpet.module.experience.mapper.RewardDisputeMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardDisputeServiceTest {

    @Mock private RewardDisputeMapper disputes;
    @Mock private ExperienceLockMapper locks;
    @Mock private PostMapper posts;
    @Mock private ClueMapper clues;
    @Mock private SysConfigService config;
    @Mock private MessageService messages;
    @InjectMocks private RewardDisputeService service;

    private static final long POST = 5L;
    private static final long USER = 2L;

    private Post frozenPost() {
        Post post = new Post();
        post.setId(POST);
        post.setUserId(1L);
        post.setRewardStatus("FROZEN");
        post.setStatus("REWARD_FROZEN");
        return post;
    }

    @Test
    @DisplayName("非线索提供者不能发起找回争议")
    void rejectNonClueProvider() {
        when(locks.lockPost(POST)).thenReturn(frozenPost());
        when(clues.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.submit(USER, POST, "我确实帮忙找到了".repeat(2), List.of("https://a/e.jpg")))
                .isInstanceOf(BizException.class);
        verify(disputes, never()).insert(any(RewardDispute.class));
    }

    @Test
    @DisplayName("已存在待核验争议时拒绝重复提交")
    void rejectDuplicatePending() {
        when(locks.lockPost(POST)).thenReturn(frozenPost());
        when(clues.selectCount(any())).thenReturn(1L);
        when(disputes.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(USER, POST, "我确实帮忙找到了".repeat(2), List.of("https://a/e.jpg")))
                .isInstanceOf(BizException.class);
        verify(disputes, never()).insert(any(RewardDispute.class));
    }

    @Test
    @DisplayName("线索提供者首次提交争议成功落库并通知发布者")
    void submitSuccess() {
        when(locks.lockPost(POST)).thenReturn(frozenPost());
        when(clues.selectCount(any())).thenReturn(1L);
        when(disputes.selectCount(any())).thenReturn(0L);

        service.submit(USER, POST, "我确实帮忙找到了".repeat(2), List.of("https://a/e.jpg"));

        verify(disputes).insert(any(RewardDispute.class));
        verify(messages).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("resolve 非法结论被拒绝")
    void rejectInvalidDecision() {
        RewardDispute dispute = new RewardDispute();
        dispute.setId(9L);
        dispute.setPostId(POST);
        dispute.setStatus("PENDING");
        when(disputes.selectById(9L)).thenReturn(dispute);
        when(locks.lockPost(POST)).thenReturn(frozenPost());

        assertThatThrownBy(() -> service.resolve(100L, 9L, "UNKNOWN", "说明理由足够长"))
                .isInstanceOf(BizException.class);
        verify(disputes, never()).updateById(any(RewardDispute.class));
    }

    @Test
    @DisplayName("ensureRefundAllowed 在有未结争议时阻止退款")
    void blockRefundWhenDisputeOpen() {
        when(locks.lockPost(POST)).thenReturn(frozenPost());
        when(disputes.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.ensureRefundAllowed(POST))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("ensureRefundAllowed 在异议窗口内阻止退款")
    void blockRefundWithinObjectionWindow() {
        Post post = frozenPost();
        post.setRewardExpireTime(java.time.LocalDateTime.now());
        when(locks.lockPost(POST)).thenReturn(post);
        when(disputes.selectCount(any())).thenReturn(0L);
        when(clues.selectCount(any())).thenReturn(0L);
        when(config.getInt("reward.refund.objection.hours", 72)).thenReturn(72);

        assertThatThrownBy(() -> service.ensureRefundAllowed(POST))
                .isInstanceOf(BizException.class);
    }
}
