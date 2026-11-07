package com.jaycode.rongpet.module.experience;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.*;
import com.jaycode.rongpet.module.experience.entity.*;
import com.jaycode.rongpet.module.experience.mapper.*;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.module.pet.service.PetService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.feeding.entity.FeedingRequest;
import com.jaycode.rongpet.module.feeding.mapper.FeedingRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExperienceService {
    public static final int PAGE_SIZE = 20;
    private final UserFavoriteMapper favorites;
    private final FeedbackMapper feedbacks;
    private final FeedbackReplyMapper replies;
    private final PetRoomMapper rooms;
    private final PostMapper postMapper;
    private final ClueMapper clues;
    private final FeedingRequestMapper feeding;
    private final PostService posts;
    private final PetService pets;
    private final MessageService messages;
    private final SysConfigService config;

    @Transactional
    public void favorite(long user, long post, boolean on) {
        if (on) {
            Post target = postMapper.selectById(post);
            if (!visible(target, user)) throw new BizException(CommonErrorCode.NOT_FOUND, "帖子不可见");
            favorites.restoreOrInsert(IdUtil.getSnowflakeNextId(), user, post);
        } else {
            favorites.delete(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, user).eq(UserFavorite::getPostId, post));
        }
    }

    public List<FavoriteItem> favorites(long user, int page) {
        validatePage(page);
        List<UserFavorite> items = favorites.selectPage(new Page<>(page, PAGE_SIZE, false),
                new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, user)
                        .orderByDesc(UserFavorite::getCreateTime).orderByDesc(UserFavorite::getId)).getRecords();
        if (items.isEmpty()) return List.of();
        // 一次批量查询，不调用会累加浏览量的详情接口，不加载分账或私人材料。
        Map<Long, Post> batch = postMapper.selectBatchIds(items.stream().map(UserFavorite::getPostId).toList())
                .stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        return items.stream().map(item -> {
            Post post = batch.get(item.getPostId());
            boolean unavailable = !visible(post, user);
            return new FavoriteItem(item.getPostId(), unavailable, unavailable ? null : posts.toCard(post));
        }).toList();
    }

    private boolean visible(Post post, long user) {
        if (post == null) return false;
        if (Objects.equals(post.getUserId(), user)) return true;
        if (Set.of("SHOWING", "REWARD_FROZEN", "COMPLETED").contains(post.getStatus())) return true;
        return "EXPIRED".equals(post.getStatus()) && post.getExpireTime() != null
                && !post.getExpireTime().isBefore(LocalDateTime.now().minusDays(config.getInt("post.expired.retain.days", 30)));
    }

    public Overview overview(long user) {
        List<Post> owned = postMapper.selectList(new LambdaQueryWrapper<Post>().eq(Post::getUserId, user));
        long pending = owned.isEmpty() ? 0 : clues.selectCount(new LambdaQueryWrapper<Clue>()
                .in(Clue::getPostId, owned.stream().map(Post::getId).toList()).eq(Clue::getStatus, "PENDING"));
        long requests = feeding.selectCount(new LambdaQueryWrapper<FeedingRequest>()
                .eq(FeedingRequest::getUserId, user).in(FeedingRequest::getStatus, "PENDING", "CONTACTED"));
        return new Overview(pending, requests, messages.unreadCount(user), posts.mine(user, "showing", 1, 5));
    }

    public static void validatePage(int page) {
        if (page < 1 || page > 10000) throw new BizException(CommonErrorCode.BAD_REQUEST, "页码不合法");
    }

    @Transactional
    public long feedback(long user, String category, String content) {
        if (content.trim().length() < 5 || content.length() > 1000) throw new BizException(CommonErrorCode.BAD_REQUEST, "反馈内容需5至1000字");
        Feedback item = new Feedback();
        item.setUserId(user);
        item.setCategory(category);
        item.setContent(content.trim());
        item.setStatus("PENDING");
        feedbacks.insert(item);
        return item.getId();
    }

    public List<FeedbackItem> feedbacks(Long user, int page) {
        validatePage(page);
        return feedbacks.selectPage(new Page<>(page, PAGE_SIZE, false), new LambdaQueryWrapper<Feedback>()
                .eq(user != null, Feedback::getUserId, user).orderByDesc(Feedback::getCreateTime).orderByDesc(Feedback::getId))
                .getRecords().stream().map(f -> new FeedbackItem(f.getId(), f.getCategory(), f.getContent(), f.getStatus(), f.getCreateTime())).toList();
    }

    public FeedbackDetail feedbackDetail(Long user, long id) {
        Feedback item = feedbacks.selectById(id);
        if (item == null || user != null && !Objects.equals(item.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "反馈不存在");
        List<ReplyItem> history = replies.selectList(new LambdaQueryWrapper<FeedbackReply>().eq(FeedbackReply::getFeedbackId, id)
                .orderByAsc(FeedbackReply::getCreateTime).orderByAsc(FeedbackReply::getId)).stream()
                .map(reply -> new ReplyItem(reply.getContent(), reply.getCreateTime())).toList();
        return new FeedbackDetail(item.getId(), item.getCategory(), item.getContent(), item.getStatus(), item.getCreateTime(), history);
    }

    @Transactional
    public void reply(long admin, long id, String content) {
        Feedback feedback = feedbacks.lock(id);
        if (feedback == null) throw new BizException(CommonErrorCode.NOT_FOUND, "反馈不存在");
        FeedbackReply reply = new FeedbackReply();
        reply.setFeedbackId(id);
        reply.setAdminId(admin);
        reply.setContent(content);
        replies.insert(reply);
        feedback.setStatus("REPLIED");
        if (feedbacks.updateById(feedback) != 1) throw new BizException(CommonErrorCode.CONFLICT, "反馈已更新，请刷新");
        messages.send(feedback.getUserId(), MessageService.TYPE_SYSTEM, "反馈收到回复", "运营人员已回复你的建议", "FEEDBACK", String.valueOf(id));
    }

    @Transactional
    public void scene(long user, long pet, String scene) {
        pets.requireOwned(user, pet);
        if (!Set.of("SUNNY", "GARDEN", "NIGHT").contains(scene)
                || !Arrays.asList(config.get("pet.room.scenes", "SUNNY,GARDEN,NIGHT").split(",")).contains(scene))
            throw new BizException(CommonErrorCode.BAD_REQUEST, "场景不存在");
        rooms.saveScene(IdUtil.getSnowflakeNextId(), pet, user, scene);
    }

    public Room room(long user, long pet) {
        pets.requireOwned(user, pet);
        PetRoom room = rooms.selectOne(new LambdaQueryWrapper<PetRoom>().eq(PetRoom::getPetId, pet).eq(PetRoom::getUserId, user));
        return new Room(room == null ? "SUNNY" : room.getScene());
    }
}
