package com.jaycode.rongpet.module.clue.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.clue.dto.ClueSubmitRequest;
import com.jaycode.rongpet.module.clue.dto.ClueVO;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.error.ClueErrorCode;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.security.SensitiveWordService;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 线索服务：提交（单帖单人限 3 次）、发布者查看/标记有效/无效。
 */
@Service
@RequiredArgsConstructor
public class ClueService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final ClueMapper clueMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CryptoService cryptoService;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;
    private final SensitiveWordService sensitiveWordService;

    @Transactional
    public ClueVO submit(long userId, long postId, ClueSubmitRequest request) {
        Post post = requirePost(postId);
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        if (post.getUserId() == userId) {
            throw new BizException(ClueErrorCode.CLUE_SELF);
        }
        if (!PHONE.matcher(request.getContactPhone()).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写正确的手机号");
        }
        // 敏感词/导流拦截（PRD FR-4.3）：线索内容命中风险词库不予提交，避免导流与违规信息
        if (sensitiveWordService.containsSensitive(request.getContent())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "线索内容包含违规信息，请修改后再提交");
        }
        long submitted = clueMapper.selectCount(new LambdaQueryWrapper<Clue>()
                .eq(Clue::getPostId, postId)
                .eq(Clue::getUserId, userId));
        if (submitted >= sysConfigService.getLong("clue.per.user.max", 3)) {
            throw new BizException(ClueErrorCode.CLUE_LIMIT);
        }
        String contactHmac = cryptoService.hmacIndex(request.getContactPhone());
        if (contactHmac.equals(post.getContactHmac())) {
            throw new BizException(ClueErrorCode.CLUE_CHEAT);
        }

        Clue clue = new Clue();
        clue.setPostId(postId);
        clue.setUserId(userId);
        clue.setContent(request.getContent());
        clue.setImages(request.getImages() == null || request.getImages().isEmpty()
                ? null : JSONUtil.toJsonStr(request.getImages()));
        clue.setContactEnc(cryptoService.encrypt(request.getContactPhone()));
        clue.setContactHmac(contactHmac);
        clue.setStatus("PENDING");
        clue.setPayStatus("UNPAID");
        clue.setShareAmount(0L);
        clue.setDetailPublic(0);
        clueMapper.insert(clue);
        incrementClueCount(postId);

        messageService.send(post.getUserId(), MessageService.TYPE_ORDER, "收到新线索",
                "您的帖子《" + post.getTitle() + "》收到一条新线索", "POST", String.valueOf(postId));
        return toVO(clue, true);
    }

    /** 发布者查看全部线索；线索提交者仅能查看自己的线索 */
    public List<ClueVO> list(long viewerId, long postId) {
        Post post = requirePost(postId);
        boolean owner = post.getUserId() == viewerId;
        LambdaQueryWrapper<Clue> wrapper = new LambdaQueryWrapper<Clue>()
                .eq(Clue::getPostId, postId)
                .orderByDesc(Clue::getCreateTime);
        if (!owner) {
            wrapper.eq(Clue::getUserId, viewerId);
        }
        return clueMapper.selectList(wrapper).stream()
                .map(c -> toVO(c, c.getUserId() == viewerId))
                .toList();
    }

    @Transactional
    public void markValid(long userId, long clueId) {
        Clue clue = requireClue(clueId);
        Post post = requireOwner(postIdOf(clue), userId);
        if (clue.getUserId().equals(post.getUserId())) {
            throw new BizException(ClueErrorCode.CLUE_CHEAT);
        }
        if (!"PENDING".equals(clue.getStatus())) {
            throw new BizException(CommonErrorCode.CONFLICT, "线索已处理，请刷新后重试");
        }
        long validCount = clueMapper.selectCount(new LambdaQueryWrapper<Clue>()
                .eq(Clue::getPostId, post.getId())
                .eq(Clue::getStatus, "VALID"));
        long max = sysConfigService.getLong("clue.valid.max", 5);
        if (validCount >= max) {
            throw new BizException(ClueErrorCode.CLUE_VALID_LIMIT);
        }
        Clue update = new Clue();
        update.setId(clueId);
        update.setStatus("VALID");
        clueMapper.updateById(update);
        messageService.send(clue.getUserId(), MessageService.TYPE_ORDER, "线索被标记有效",
                "您对帖子《" + post.getTitle() + "》提交的线索已被标记为有效，请等待失主交接",
                "CLUE", String.valueOf(clueId));
    }

    @Transactional
    public void markInvalid(long userId, long clueId, String reason) {
        Clue clue = requireClue(clueId);
        Post post = requireOwner(postIdOf(clue), userId);
        if (!"PENDING".equals(clue.getStatus()) && !"VALID".equals(clue.getStatus())) {
            throw new BizException(CommonErrorCode.CONFLICT, "线索已处理，请刷新后重试");
        }
        Clue update = new Clue();
        update.setId(clueId);
        update.setStatus("INVALID");
        update.setInvalidReason(reason);
        clueMapper.updateById(update);
        messageService.send(clue.getUserId(), MessageService.TYPE_ORDER, "线索未通过",
                "您对帖子《" + post.getTitle() + "》提交的线索未被采纳" + (StringUtils.hasText(reason) ? "：" + reason : ""),
                "CLUE", String.valueOf(clueId));
    }

    public List<Clue> validClues(long postId) {
        return clueMapper.selectList(new LambdaQueryWrapper<Clue>()
                .eq(Clue::getPostId, postId)
                .eq(Clue::getStatus, "VALID")
                .orderByAsc(Clue::getCreateTime));
    }

    /**
     * 线索 48 小时未处理提醒（PRD FR-5.2.6）：按帖子聚合提醒发布者一次，提醒后写入 remind_time 防重复。
     */
    @Transactional
    public int remindPending() {
        LocalDateTime now = LocalDateTime.now();
        List<Clue> clues = clueMapper.selectList(new LambdaQueryWrapper<Clue>()
                .eq(Clue::getStatus, "PENDING")
                .lt(Clue::getCreateTime, now.minusHours(48))
                .isNull(Clue::getRemindTime)
                .last("limit 500"));
        if (clues.isEmpty()) {
            return 0;
        }
        // 仅提醒仍在展示/托管中的帖子，避免已结束帖子打扰发布者
        List<Long> postIds = clues.stream().map(Clue::getPostId).distinct().toList();
        Map<Long, Post> posts = postMapper.selectBatchIds(postIds).stream()
                .filter(p -> PostStatus.visible(p.getStatus()))
                .collect(Collectors.toMap(Post::getId, p -> p));
        Map<Long, Long> counts = clues.stream()
                .filter(c -> posts.containsKey(c.getPostId()))
                .collect(Collectors.groupingBy(Clue::getPostId, Collectors.counting()));

        int reminded = 0;
        for (Clue clue : clues) {
            Clue update = new Clue();
            update.setId(clue.getId());
            update.setRemindTime(now);
            clueMapper.updateById(update);
            reminded++;
        }
        counts.forEach((postId, count) -> {
            Post post = posts.get(postId);
            messageService.send(post.getUserId(), MessageService.TYPE_ORDER, "线索待处理提醒",
                    "您的帖子《" + post.getTitle() + "》有 " + count + " 条线索超过48小时未处理，请及时标记有效或驳回",
                    "POST", String.valueOf(postId));
        });
        return reminded;
    }

    public ClueVO toVO(Clue clue, boolean mine) {
        ClueVO vo = new ClueVO();
        vo.setId(clue.getId());
        vo.setPostId(clue.getPostId());
        vo.setUserId(clue.getUserId());
        vo.setNickname(nickname(clue.getUserId()));
        vo.setContent(clue.getContent());
        vo.setImages(clue.getImages() == null ? List.of()
                : JSONUtil.toList(clue.getImages(), String.class));
        vo.setContactMasked(CryptoService.maskPhone(cryptoService.decrypt(clue.getContactEnc())));
        vo.setStatus(clue.getStatus());
        vo.setInvalidReason(clue.getInvalidReason());
        vo.setScore(clue.getScore());
        vo.setScoreComment(clue.getScoreComment());
        vo.setScoreSource(clue.getScoreSource());
        vo.setShareAmount(clue.getShareAmount());
        vo.setPayStatus(clue.getPayStatus());
        vo.setCreateTime(clue.getCreateTime());
        return vo;
    }

    private long postIdOf(Clue clue) {
        return clue.getPostId();
    }

    private Clue requireClue(long clueId) {
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new BizException(ClueErrorCode.CLUE_NOT_FOUND);
        }
        return clue;
    }

    private Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private Post requireOwner(long postId, long userId) {
        Post post = requirePost(postId);
        if (post.getUserId() != userId) {
            throw new BizException(ClueErrorCode.CLUE_ONLY_PUBLISHER);
        }
        return post;
    }

    private void incrementClueCount(long postId) {
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Post>()
                .setSql("clue_count = clue_count + 1")
                .eq("id", postId));
    }

    private String nickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || user.getNickname() == null ? "宠友" : user.getNickname();
    }
}
