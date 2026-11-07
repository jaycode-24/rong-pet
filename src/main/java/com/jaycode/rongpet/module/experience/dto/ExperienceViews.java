package com.jaycode.rongpet.module.experience.dto;

import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import java.time.LocalDateTime;
import java.util.List;

public final class ExperienceViews {
    private ExperienceViews() {}
    public record FavoriteItem(Long postId, boolean unavailable, PostCardVO post) {}
    public record Overview(long pendingClues, long feedingRequests, long unreadMessages, PageResult<PostCardVO> posts) {}
    public record FeedbackItem(Long id, String category, String content, String status, LocalDateTime createTime) {}
    public record ReplyItem(String content, LocalDateTime createTime) {}
    public record FeedbackDetail(Long id, String category, String content, String status, LocalDateTime createTime, List<ReplyItem> replies) {}
    public record Room(String scene) {}
    public record ConversationItem(Long id, Long postId, LocalDateTime createTime) {}
    public record ChatMessage(Long id, Long senderId, String content, LocalDateTime createTime) {}
    public record AvatarQuota(long used, long remaining, long priceCents, boolean providerConfigured) {}
    public record AvatarTask(Long id, Long petId, String style, Integer freeUse, Long price, String status, String resultUrl, String errorMessage, LocalDateTime createTime) {}
    public record DisputeItem(Long id, Long postId, Long userId, String content, List<String> evidence, String status, String decision, LocalDateTime createTime) {}
    public record FundItem(String flowNo, String bizType, String bizNo, Long amount, String status, String voucherNo, String userDirection, LocalDateTime createTime, LocalDateTime finishTime) {}
}
