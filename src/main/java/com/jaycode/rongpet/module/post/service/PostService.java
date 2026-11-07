package com.jaycode.rongpet.module.post.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.claim.entity.Claim;
import com.jaycode.rongpet.module.claim.mapper.ClaimMapper;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.pet.entity.Pet;
import com.jaycode.rongpet.module.pet.mapper.PetMapper;
import com.jaycode.rongpet.module.post.constant.ChengduDistrict;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.PostType;
import com.jaycode.rongpet.module.post.constant.RewardStatus;
import com.jaycode.rongpet.module.post.dto.FoundConfirmRequest;
import com.jaycode.rongpet.module.post.dto.FoundVO;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import com.jaycode.rongpet.module.post.dto.PostCreateRequest;
import com.jaycode.rongpet.module.post.dto.PostDetailVO;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.entity.PostLog;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostLogMapper;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.split.service.SplitService;
import com.jaycode.rongpet.security.SensitiveWordService;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 帖子服务：发布、编辑、审核、列表排序、详情、确认找回、到期。
 * 状态流转全部在服务端控制并写入 t_post_log。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Set<String> TYPES = Set.of(PostType.LOST, PostType.FOUND, PostType.BREED);
    private static final Set<String> PET_TYPES = Set.of("CAT", "DOG", "EXOTIC");
    private static final List<String> BREED_FORBIDDEN = List.of(
            "出售", "售卖", "价格", "包配", "定金", "幼犬可售", "幼猫可售", "有偿领养", "领养费");

    private final PostMapper postMapper;
    private final PostLogMapper postLogMapper;
    private final UserMapper userMapper;
    private final PetMapper petMapper;
    private final ClueMapper clueMapper;
    private final ClaimMapper claimMapper;
    private final SplitService splitService;
    private final CryptoService cryptoService;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;
    private final SensitiveWordService sensitiveWordService;

    // ------------------------------------------------------------------ 发布 / 编辑

    @Transactional
    public PostDetailVO create(long userId, PostCreateRequest request) {
        User publisher = userMapper.selectById(userId);
        if (publisher == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        if (UserStatus.BANNED.equals(publisher.getStatus())
                || UserStatus.FROZEN.equals(publisher.getStatus())
                || UserStatus.LIMIT_POST.equals(publisher.getStatus())) {
            throw new BizException(PostErrorCode.USER_FORBIDDEN);
        }
        validate(request, userId);
        boolean hasReward = PostType.LOST.equals(request.getType())
                && request.getRewardAmount() != null && request.getRewardAmount() > 0;
        if (hasReward) {
            if (publisher.getRegisterTime() != null
                    && publisher.getRegisterTime().isAfter(LocalDateTime.now().minusDays(7))) {
                long normalPosts = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .in(Post::getStatus, PostStatus.PENDING_AUDIT, PostStatus.SHOWING,
                                PostStatus.REWARD_FROZEN, PostStatus.WAIT_SCORE,
                                PostStatus.SPLITTING, PostStatus.COMPLETED)
                        .eq(Post::getRewardAmount, 0));
                if (normalPosts == 0) {
                    throw new BizException(PostErrorCode.NEW_USER_REWARD_FORBIDDEN);
                }
            }
            long inFlight = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                    .eq(Post::getUserId, userId)
                    .eq(Post::getStatus, PostStatus.REWARD_FROZEN)
                    .eq(Post::getRewardStatus, RewardStatus.FROZEN));
            if (inFlight >= 2) {
                throw new BizException(PostErrorCode.REWARD_IN_FLIGHT_LIMIT);
            }
        }
        Post post = new Post();
        apply(post, request, userId);
        post.setUserId(userId);
        post.setStatus(Boolean.TRUE.equals(request.getDraft()) ? PostStatus.DRAFT : PostStatus.PENDING_AUDIT);
        post.setViewCount(0);
        post.setClueCount(0);
        post.setClaimCount(0);
        post.setEditCount(0);
        post.setExtendCount(0);
        post.setGratuityAmount(0L);
        if (post.getRewardAmount() == null) {
            post.setRewardAmount(0L);
            post.setRewardStatus(RewardStatus.NONE);
        }
        postMapper.insert(post);
        maybeSavePet(userId, post, request);
        markSensitiveIfNeeded(post);
        log(post.getId(), userId, "USER", "CREATE", null, post.getStatus(), null);
        return detail(userId, post.getId());
    }

    @Transactional
    public PostDetailVO update(long userId, long postId, PostCreateRequest request) {
        Post post = requireOwner(postId, userId);
        if (PostStatus.COMPLETED.equals(post.getStatus()) || PostStatus.EXPIRED.equals(post.getStatus())
                || PostStatus.TAKEN_DOWN.equals(post.getStatus()) || PostStatus.SPLITTING.equals(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        boolean visible = PostStatus.visible(post.getStatus());
        if (visible) {
            int editCount = post.getEditCount() == null ? 0 : post.getEditCount();
            if (editCount >= 1) {
                throw new BizException(PostErrorCode.EDIT_LIMIT);
            }
            post.setEditCount(editCount + 1);
        }
        validate(request, userId);
        String from = post.getStatus();
        apply(post, request, userId);
        post.setStatus(PostStatus.PENDING_AUDIT);
        postMapper.updateById(post);
        markSensitiveIfNeeded(post);
        log(postId, userId, "USER", "EDIT", from, post.getStatus(), null);
        return detail(userId, postId);
    }

    /**
     * 敏感词命中不直接拦截提交，保持 PENDING_AUDIT 并写审核备注，进入人工复核队列且不对外展示（PRD FR-4.3）。
     */
    private void markSensitiveIfNeeded(Post post) {
        if (PostStatus.DRAFT.equals(post.getStatus())) {
            return;
        }
        List<String> hits = sensitiveWordService.scan(post.getTitle(), post.getDescription(),
                post.getExpectCondition());
        if (hits.isEmpty()) {
            return;
        }
        String remark = "敏感词命中待人工复核：" + String.join("、", hits);
        Post mark = new Post();
        mark.setId(post.getId());
        mark.setAuditRemark(remark);
        postMapper.updateById(mark);
        post.setAuditRemark(remark);
    }

    // ------------------------------------------------------------------ 查询

    public PageResult<PostCardVO> list(String type, String district, String sort, String keyword,
                                       int pageNum, int pageSize) {
        // 已过期帖子保留展示 30 天（PRD FR-5.2.10），之后仅本人可见
        LocalDateTime retainedAfter = LocalDateTime.now()
                .minusDays(sysConfigService.getInt("post.expired.retain.days", 30));
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .and(w -> w.in(Post::getStatus, PostStatus.SHOWING, PostStatus.REWARD_FROZEN)
                        .or(x -> x.eq(Post::getStatus, PostStatus.EXPIRED)
                                .ge(Post::getExpireTime, retainedAfter)))
                .eq(StringUtils.hasText(type), Post::getType, type)
                .eq(StringUtils.hasText(district), Post::getDistrict, district)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Post::getTitle, keyword)
                        .or().like(Post::getDescription, keyword)
                        .or().like(Post::getBreed, keyword));
        String order = "reward".equals(sort)
                ? "ORDER BY (top_expire_time IS NOT NULL AND top_expire_time > NOW()) DESC, "
                + "(reward_status = 'FROZEN') DESC, reward_amount DESC, create_time DESC"
                : "ORDER BY (top_expire_time IS NOT NULL AND top_expire_time > NOW()) DESC, create_time DESC";
        wrapper.last(order);
        Page<Post> page = postMapper.selectPage(new Page<>(normalize(pageNum), normalize(pageSize)), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toCard).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<PostCardVO> mine(long userId, String group, int pageNum, int pageSize) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .orderByDesc(Post::getCreateTime);
        if (StringUtils.hasText(group)) {
            switch (group) {
                case "showing" -> wrapper.in(Post::getStatus, PostStatus.SHOWING, PostStatus.REWARD_FROZEN, PostStatus.WAIT_SCORE, PostStatus.SPLITTING);
                case "audit" -> wrapper.in(Post::getStatus, PostStatus.PENDING_AUDIT, PostStatus.DRAFT);
                case "ended" -> wrapper.in(Post::getStatus, PostStatus.COMPLETED, PostStatus.EXPIRED, PostStatus.TAKEN_DOWN);
                case "rejected" -> wrapper.eq(Post::getStatus, PostStatus.REJECTED);
                default -> {
                }
            }
        }
        Page<Post> page = postMapper.selectPage(new Page<>(normalize(pageNum), normalize(pageSize)), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toCard).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<PostCardVO> adminPage(String status, String type, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(StringUtils.hasText(status), Post::getStatus, status)
                .eq(StringUtils.hasText(type), Post::getType, type)
                .like(StringUtils.hasText(keyword), Post::getTitle, keyword)
                .orderByAsc(Post::getCreateTime);
        Page<Post> page = postMapper.selectPage(new Page<>(normalize(pageNum), normalize(pageSize)), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toCard).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public PostDetailVO detail(Long viewerId, long postId) {
        Post post = requirePost(postId);
        // 已过期超过保留期的帖子仅本人可见（PRD FR-5.2.10），后台走 adminDetail 不受限
        if (PostStatus.EXPIRED.equals(post.getStatus())
                && (viewerId == null || viewerId != post.getUserId())) {
            int retainDays = sysConfigService.getInt("post.expired.retain.days", 30);
            if (post.getExpireTime() != null && post.getExpireTime().isBefore(LocalDateTime.now().minusDays(retainDays))) {
                throw new BizException(PostErrorCode.POST_NOT_FOUND);
            }
        }
        incrementView(postId);
        post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        return toDetail(post, viewerId);
    }

    /** 后台详情：不增加浏览量 */
    public PostDetailVO adminDetail(long postId) {
        return toDetail(requirePost(postId), null);
    }

    /** 帖子操作日志（后台排查用） */
    public List<PostLog> logs(long postId) {
        return postLogMapper.selectList(new LambdaQueryWrapper<PostLog>()
                .eq(PostLog::getPostId, postId)
                .orderByAsc(PostLog::getCreateTime));
    }

    private PostDetailVO toDetail(Post post, Long viewerId) {
        PostDetailVO vo = new PostDetailVO();
        vo.setId(post.getId());
        vo.setType(post.getType());
        vo.setTitle(post.getTitle());
        vo.setPetType(post.getPetType());
        vo.setBreed(post.getBreed());
        vo.setGender(post.getGender());
        vo.setColor(post.getColor());
        vo.setSize(post.getSize());
        vo.setSterilized(post.getSterilized());
        vo.setChipFeature(post.getChipFeature());
        vo.setAge(post.getAge());
        vo.setWeight(post.getWeight());
        vo.setVaccineStatus(post.getVaccineStatus());
        vo.setDewormStatus(post.getDewormStatus());
        vo.setGeneticScreening(post.getGeneticScreening());
        vo.setBreedIntent(post.getBreedIntent());
        vo.setExpectCondition(post.getExpectCondition());
        vo.setDistrict(post.getDistrict());
        vo.setLocation(post.getLocation());
        vo.setLocationLatLng(post.getLocationLatLng());
        vo.setEventTime(post.getEventTime());
        vo.setDescription(post.getDescription());
        vo.setImages(parseList(post.getImages()));
        vo.setCoverUrl(post.getCoverUrl());
        vo.setRewardAmount(post.getRewardAmount());
        vo.setRewardStatus(post.getRewardStatus());
        vo.setRewardDays(post.getRewardDays());
        vo.setRewardExpireTime(post.getRewardExpireTime());
        vo.setGratuityAmount(post.getGratuityAmount());
        vo.setStatus(post.getStatus());
        vo.setRejectReason(post.getRejectReason());
        vo.setTopExpireTime(post.getTopExpireTime());
        vo.setExpireTime(post.getExpireTime());
        vo.setViewCount(post.getViewCount());
        vo.setClueCount(post.getClueCount());
        vo.setClaimCount(post.getClaimCount());
        vo.setFoundTime(post.getFoundTime());
        vo.setFoundNote(post.getFoundNote());
        vo.setFoundProof(parseList(post.getFoundProof()));
        vo.setPublisherNickname(nickname(post.getUserId()));
        vo.setContactMasked(null);
        vo.setOwner(viewerId != null && viewerId == post.getUserId());
        vo.setCreateTime(post.getCreateTime());
        if (post.getRewardAmount() != null && post.getRewardAmount() > 0) {
            vo.setSplitSummary(splitService.summary(post.getId(), viewerId, post.getUserId()));
        }
        return vo;
    }

    /** 疑似对端匹配：品种一致 + 区县相同 + 时间窗前后 7 天 */
    public List<PostCardVO> match(String type, String petType, String breed, String district, LocalDateTime eventTime) {
        String opposite = PostType.LOST.equals(type) ? PostType.FOUND : PostType.LOST;
        LocalDateTime center = eventTime == null ? LocalDateTime.now() : eventTime;
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .in(Post::getStatus, PostStatus.SHOWING, PostStatus.REWARD_FROZEN)
                .eq(Post::getType, opposite)
                .eq(StringUtils.hasText(petType), Post::getPetType, petType)
                .eq(StringUtils.hasText(district), Post::getDistrict, district)
                .eq(StringUtils.hasText(breed), Post::getBreed, breed)
                .between(Post::getEventTime, center.minusDays(7), center.plusDays(7))
                .orderByDesc(Post::getCreateTime)
                .last("limit 5"));
        return posts.stream().map(this::toCard).toList();
    }

    public PageResult<FoundVO> foundList(int pageNum, int pageSize) {
        Page<Post> page = postMapper.selectPage(new Page<>(normalize(pageNum), normalize(pageSize)),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, PostStatus.COMPLETED)
                        .isNotNull(Post::getFoundTime)
                        .in(Post::getType, PostType.LOST, PostType.FOUND)
                        .orderByDesc(Post::getFoundTime));
        return PageResult.of(page.getRecords().stream().map(this::toFound).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ------------------------------------------------------------------ 用户操作

    @Transactional
    public void takeDown(long userId, long postId) {
        Post post = requireOwner(postId, userId);
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        if (RewardStatus.FROZEN.equals(post.getRewardStatus())) {
            throw new BizException(PostErrorCode.REWARD_FROZEN_NO_TAKEDOWN);
        }
        Post update = new Post();
        update.setId(postId);
        update.setStatus(PostStatus.TAKEN_DOWN);
        postMapper.updateById(update);
        log(postId, userId, "USER", "TAKE_DOWN", post.getStatus(), PostStatus.TAKEN_DOWN, null);
    }

    @Transactional
    public void extend(long userId, long postId) {
        Post post = requireOwner(postId, userId);
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        if (post.getExtendCount() != null && post.getExtendCount() >= 1) {
            throw new BizException(CommonErrorCode.CONFLICT, "每帖仅可延长一次");
        }
        int days = post.getRewardAmount() != null && post.getRewardAmount() > 0 && post.getRewardDays() != null
                ? post.getRewardDays()
                : sysConfigService.getInt("post.default.days", 7);
        Post update = new Post();
        update.setId(postId);
        update.setExtendCount(1);
        if (RewardStatus.FROZEN.equals(post.getRewardStatus()) && post.getRewardExpireTime() != null) {
            update.setRewardExpireTime(post.getRewardExpireTime().plusDays(days));
        }
        update.setExpireTime((post.getExpireTime() == null ? LocalDateTime.now() : post.getExpireTime()).plusDays(days));
        postMapper.updateById(update);
        log(postId, userId, "USER", "EXTEND", post.getStatus(), post.getStatus(), "延长 " + days + " 天");
    }

    @Transactional
    public void confirmFound(long userId, long postId, FoundConfirmRequest request) {
        jdbc.queryForList("SELECT id FROM t_post WHERE id=? FOR UPDATE",postId);
        Post post = requireOwner(postId, userId);
        if (!PostType.LOST.equals(post.getType())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        // 幂等：已确认找回或已进入评分/分账流程时直接返回
        if (PostStatus.WAIT_SCORE.equals(post.getStatus())
                || PostStatus.SPLITTING.equals(post.getStatus())
                || PostStatus.COMPLETED.equals(post.getStatus())) {
            return;
        }
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        boolean hasReward = post.getRewardAmount() != null && post.getRewardAmount() > 0;
        if (hasReward) {
            // 悬赏必须先完成支付托管，避免未收款即进入分账
            if (!RewardStatus.FROZEN.equals(post.getRewardStatus())) {
                throw new BizException(PostErrorCode.REWARD_NOT_PAID);
            }
            List<Clue> valid = clueMapper.selectList(new LambdaQueryWrapper<Clue>()
                    .eq(Clue::getPostId, postId)
                    .eq(Clue::getStatus, "VALID"));
            if (valid.isEmpty()) {
                throw new BizException(PostErrorCode.NOT_FOUND_CONFIRM_REQUIRED);
            }
        }
        Post update = new Post();
        update.setId(postId);
        update.setFoundTime(LocalDateTime.now());
        update.setFoundNote(request.getFoundNote());
        update.setFoundProof(JSONUtil.toJsonStr(request.getProofImages()));
        update.setStatus(hasReward ? PostStatus.WAIT_SCORE : PostStatus.COMPLETED);
        postMapper.updateById(update);
        log(postId, userId, "USER", "CONFIRM_FOUND", post.getStatus(), update.getStatus(), null);

        if (hasReward) {
            messageService.send(userId, MessageService.TYPE_ORDER, "请完成线索评分",
                    "帖子《" + post.getTitle() + "》已确认找回，请在7天内完成评分", "POST", String.valueOf(postId));
            clueMapper.selectList(new LambdaQueryWrapper<Clue>()
                            .eq(Clue::getPostId, postId).eq(Clue::getStatus, "VALID"))
                    .forEach(c -> messageService.send(c.getUserId(), MessageService.TYPE_ORDER, "宠物已找到",
                            "感谢您的线索，失主已确认找回", "POST", String.valueOf(postId)));
        }
    }

    /** 普通展示帖到期下架（悬赏帖由支付模块到期退款任务处理） */
    @Transactional
    public int expireShowingPosts() {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.SHOWING)
                .isNotNull(Post::getExpireTime)
                .lt(Post::getExpireTime, LocalDateTime.now())
                .last("limit 200"));
        for (Post post : posts) {
            Post update = new Post();
            update.setId(post.getId());
            update.setStatus(PostStatus.EXPIRED);
            postMapper.updateById(update);
            log(post.getId(), null, "SYSTEM", "EXPIRE", post.getStatus(), PostStatus.EXPIRED, "到期自动下架");
            messageService.send(post.getUserId(), MessageService.TYPE_SYSTEM, "帖子已到期",
                    "帖子《" + post.getTitle() + "》已到期下架，可重新发布", "POST", String.valueOf(post.getId()));
        }
        return posts.size();
    }

    /** 草稿保留 7 天，超期逻辑删除 */
    @Transactional
    public int cleanExpiredDrafts() {
        return postMapper.delete(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.DRAFT)
                .lt(Post::getCreateTime, LocalDateTime.now().minusDays(7)));
    }

    // ------------------------------------------------------------------ 后台操作

    @Transactional
    public void approve(long adminId, long postId, String remark) {
        Post post = requirePost(postId);
        if (!PostStatus.PENDING_AUDIT.equals(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        String target = RewardStatus.FROZEN.equals(post.getRewardStatus())
                ? PostStatus.REWARD_FROZEN : PostStatus.SHOWING;
        Post update = new Post();
        update.setId(postId);
        update.setStatus(target);
        update.setAuditRemark(remark);
        update.setExpireTime(LocalDateTime.now().plusDays(sysConfigService.getInt("post.default.days", 7)));
        postMapper.updateById(update);
        log(postId, adminId, "ADMIN", "APPROVE", post.getStatus(), target, remark);
        messageService.send(post.getUserId(), MessageService.TYPE_AUDIT, "帖子审核通过",
                "帖子《" + post.getTitle() + "》已通过审核", "POST", String.valueOf(postId));
    }

    @Transactional
    public void reject(long adminId, long postId, String reason) {
        Post post = requirePost(postId);
        if (!PostStatus.PENDING_AUDIT.equals(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        Post update = new Post();
        update.setId(postId);
        update.setStatus(PostStatus.REJECTED);
        update.setRejectReason(reason);
        postMapper.updateById(update);
        log(postId, adminId, "ADMIN", "REJECT", post.getStatus(), PostStatus.REJECTED, reason);
        messageService.send(post.getUserId(), MessageService.TYPE_AUDIT, "帖子审核驳回",
                "帖子《" + post.getTitle() + "》未通过审核：" + reason, "POST", String.valueOf(postId));
    }

    @Transactional
    public void adminTakeDown(long adminId, long postId, String reason) {
        Post post = requirePost(postId);
        Post update = new Post();
        update.setId(postId);
        update.setStatus(PostStatus.TAKEN_DOWN);
        postMapper.updateById(update);
        log(postId, adminId, "ADMIN", "TAKE_DOWN", post.getStatus(), PostStatus.TAKEN_DOWN, reason);
        messageService.send(post.getUserId(), MessageService.TYPE_AUDIT, "帖子已下架",
                "帖子《" + post.getTitle() + "》已被平台下架：" + reason, "POST", String.valueOf(postId));
    }

    /** 已找回内容管理：争议期间隐藏（发布者与确认认领人可见理由），保留原状态用于恢复 */
    @Transactional
    public void hideFound(long adminId, long postId, String reason) {
        Post post = requirePost(postId);
        if (post.getFoundTime() == null || !PostStatus.COMPLETED.equals(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        Post update = new Post();
        update.setId(postId);
        update.setStatus(PostStatus.TAKEN_DOWN);
        update.setAuditRemark(reason);
        postMapper.updateById(update);
        log(postId, adminId, "ADMIN", "FOUND_HIDE", post.getStatus(), PostStatus.TAKEN_DOWN, reason);
        messageService.send(post.getUserId(), MessageService.TYPE_AUDIT, "已找回内容已隐藏",
                "帖子《" + post.getTitle() + "》因争议暂时从已找回列表隐藏：" + reason, "POST", String.valueOf(postId));
        claimMapper.selectList(new LambdaQueryWrapper<Claim>()
                        .eq(Claim::getPostId, postId)
                        .eq(Claim::getStatus, "CONFIRMED"))
                .forEach(c -> messageService.send(c.getUserId(), MessageService.TYPE_AUDIT, "已找回内容已隐藏",
                        "帖子《" + post.getTitle() + "》因争议暂时从已找回列表隐藏", "POST", String.valueOf(postId)));
    }

    /** 已找回内容恢复展示 */
    @Transactional
    public void restoreFound(long adminId, long postId) {
        Post post = requirePost(postId);
        if (post.getFoundTime() == null || !PostStatus.TAKEN_DOWN.equals(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        Post update = new Post();
        update.setId(postId);
        update.setStatus(PostStatus.COMPLETED);
        postMapper.updateById(update);
        log(postId, adminId, "ADMIN", "FOUND_RESTORE", post.getStatus(), PostStatus.COMPLETED, null);
        messageService.send(post.getUserId(), MessageService.TYPE_AUDIT, "已找回内容已恢复",
                "帖子《" + post.getTitle() + "》已恢复展示", "POST", String.valueOf(postId));
    }

    /** 已找回内容标注（争议说明、处理结论等），仅记录不改变展示状态 */
    @Transactional
    public void markFound(long adminId, long postId, String mark) {
        Post post = requirePost(postId);
        if (post.getFoundTime() == null) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        Post update = new Post();
        update.setId(postId);
        update.setAuditRemark(mark);
        postMapper.updateById(update);
        log(postId, adminId, "ADMIN", "FOUND_MARK", post.getStatus(), post.getStatus(), mark);
    }

    /** 供定时任务与后台使用 */
    public Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    public Post requireOwner(long postId, long userId) {
        Post post = requirePost(postId);
        if (post.getUserId() != userId) {
            throw new BizException(PostErrorCode.POST_NOT_OWNER);
        }
        return post;
    }

    public void log(long postId, Long operatorId, String operatorType, String action,
                    String fromStatus, String toStatus, String detail) {
        PostLog logEntry = new PostLog();
        logEntry.setPostId(postId);
        logEntry.setOperatorId(operatorId);
        logEntry.setOperatorType(operatorType);
        logEntry.setAction(action);
        logEntry.setFromStatus(fromStatus);
        logEntry.setToStatus(toStatus);
        logEntry.setDetail(detail);
        postLogMapper.insert(logEntry);
    }

    // ------------------------------------------------------------------ 校验与填充

    private void validate(PostCreateRequest request, long userId) {
        if (!TYPES.contains(request.getType())) {
            throw new BizException(PostErrorCode.POST_TYPE_INVALID);
        }
        if (!ChengduDistrict.isValid(request.getDistrict())) {
            throw new BizException(PostErrorCode.DISTRICT_INVALID);
        }
        if (request.getImages() == null || request.getImages().isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "至少上传1张图片");
        }
        int maxImages = PostType.BREED.equals(request.getType()) ? 6 : 9;
        if (request.getImages().size() > maxImages) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "图片最多" + maxImages + "张");
        }
        if (!StringUtils.hasText(request.getDescription())
                || request.getDescription().length() < 20 || request.getDescription().length() > 500) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "详细描述需20-500字");
        }
        LocalDateTime eventTime = request.getEventTime();
        if (eventTime == null || eventTime.isAfter(LocalDateTime.now())
                || eventTime.isBefore(LocalDateTime.now().minusDays(30))) {
            throw new BizException(PostErrorCode.EVENT_TIME_INVALID);
        }
        if (!StringUtils.hasText(request.getContactPhone()) || !PHONE.matcher(request.getContactPhone()).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写正确的11位手机号");
        }
        if (!Boolean.TRUE.equals(request.getAgreed())) {
            throw new BizException(PostErrorCode.AGREEMENT_REQUIRED);
        }
        if (!StringUtils.hasText(request.getPetType()) && request.getPetId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请选择宠物类型");
        }
        if (!StringUtils.hasText(request.getBreed()) && request.getPetId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写品种");
        }
        if (StringUtils.hasText(request.getPetType()) && !PET_TYPES.contains(request.getPetType())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "宠物类型不合法");
        }
        if (PostType.LOST.equals(request.getType()) && request.getRewardAmount() != null
                && request.getRewardAmount() > 0) {
            long min = sysConfigService.getLong("reward.min.cents", 10000);
            long max = sysConfigService.getLong("reward.max.cents", 1_000_000);
            if (request.getRewardAmount() < min || request.getRewardAmount() > max) {
                throw new BizException(PostErrorCode.REWARD_AMOUNT_INVALID);
            }
            if (request.getRewardDays() == null || !List.of(7, 15, 30).contains(request.getRewardDays())) {
                throw new BizException(PostErrorCode.REWARD_DAYS_INVALID);
            }
        }
        if (PostType.BREED.equals(request.getType())) {
            if (request.getRewardAmount() != null && request.getRewardAmount() > 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "相亲帖不支持悬赏");
            }
            if (request.getGender() == null || request.getAge() == null || request.getVaccineStatus() == null
                    || request.getSterilized() == null || request.getBreedIntent() == null
                    || BigDecimal.valueOf(0.5).compareTo(request.getAge()) > 0
                    || BigDecimal.valueOf(20).compareTo(request.getAge()) < 0) {
                throw new BizException(PostErrorCode.BREED_FIELD_REQUIRED);
            }
            String text = (request.getTitle() == null ? "" : request.getTitle()) + request.getDescription();
            if (BREED_FORBIDDEN.stream().anyMatch(text::contains)) {
                throw new BizException(PostErrorCode.BREED_FORBIDDEN_WORD);
            }
        }
        if (PostType.FOUND.equals(request.getType()) && request.getRewardAmount() != null
                && request.getRewardAmount() > 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "招领帖不支持悬赏");
        }
    }

    private void apply(Post post, PostCreateRequest request, long userId) {
        if (request.getPetId() != null) {
            Pet pet = petMapper.selectById(request.getPetId());
            if (pet == null || !pet.getUserId().equals(userId)) {
                throw new BizException(CommonErrorCode.NOT_FOUND, "宠物档案不存在");
            }
            post.setPetId(pet.getId());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", pet.getName());
            snapshot.put("petType", pet.getPetType());
            snapshot.put("breed", pet.getBreed());
            snapshot.put("gender", pet.getGender());
            snapshot.put("avatarUrl", pet.getAvatarUrl());
            post.setPetSnapshot(JSONUtil.toJsonStr(snapshot));
            fillFromPet(post, pet);
        }
        if (StringUtils.hasText(request.getPetType())) {
            post.setPetType(request.getPetType());
        }
        if (StringUtils.hasText(request.getBreed())) {
            post.setBreed(request.getBreed());
        }
        if (StringUtils.hasText(request.getGender())) {
            post.setGender(request.getGender());
        }
        if (StringUtils.hasText(request.getColor())) {
            post.setColor(request.getColor());
        }
        if (StringUtils.hasText(request.getSize())) {
            post.setSize(request.getSize());
        }
        if (StringUtils.hasText(request.getSterilized())) {
            post.setSterilized(request.getSterilized());
        }
        if (StringUtils.hasText(request.getChipFeature())) {
            post.setChipFeature(request.getChipFeature());
        }
        post.setAge(request.getAge());
        post.setWeight(request.getWeight());
        post.setVaccineStatus(request.getVaccineStatus());
        post.setDewormStatus(request.getDewormStatus());
        post.setGeneticScreening(request.getGeneticScreening());
        post.setBreedIntent(request.getBreedIntent());
        post.setExpectCondition(request.getExpectCondition());
        post.setType(request.getType());
        post.setDistrict(request.getDistrict());
        post.setLocation(request.getLocation());
        post.setLocationLatLng(request.getLocationLatLng());
        post.setEventTime(request.getEventTime());
        post.setContactEnc(cryptoService.encrypt(request.getContactPhone()));
        post.setContactHmac(cryptoService.hmacIndex(request.getContactPhone()));
        post.setDescription(request.getDescription());
        post.setImages(JSONUtil.toJsonStr(request.getImages()));
        post.setCoverUrl(request.getImages().get(0));
        post.setTitle(StringUtils.hasText(request.getTitle())
                ? request.getTitle()
                : defaultTitle(request.getType(), post.getBreed()));
        post.setAgreementVersion(request.getAgreementVersion());

        long reward = PostType.LOST.equals(request.getType()) && request.getRewardAmount() != null
                ? Math.max(0, request.getRewardAmount()) : 0L;
        post.setRewardAmount(reward);
        if (reward > 0) {
            post.setRewardDays(request.getRewardDays());
            post.setRewardFee(calcRewardFee(reward));
            if (!RewardStatus.FROZEN.equals(post.getRewardStatus())
                    && !RewardStatus.SPLITTING.equals(post.getRewardStatus())
                    && !RewardStatus.PAID.equals(post.getRewardStatus())) {
                post.setRewardStatus(RewardStatus.NONE);
            }
        } else {
            post.setRewardFee(0L);
            post.setRewardDays(null);
            post.setRewardStatus(RewardStatus.NONE);
        }
    }

    private void fillFromPet(Post post, Pet pet) {
        if (!StringUtils.hasText(post.getPetType())) {
            post.setPetType(pet.getPetType());
        }
        if (!StringUtils.hasText(post.getBreed())) {
            post.setBreed(pet.getBreed());
        }
        if (!StringUtils.hasText(post.getGender())) {
            post.setGender(pet.getGender());
        }
        if (!StringUtils.hasText(post.getSterilized())) {
            post.setSterilized(pet.getSterilized());
        }
        if (!StringUtils.hasText(post.getVaccineStatus())) {
            post.setVaccineStatus(pet.getVaccineStatus());
        }
        if (!StringUtils.hasText(post.getChipFeature())) {
            post.setChipFeature(pet.getChipFeature());
        }
    }

    private String defaultTitle(String type, String breed) {
        String safeBreed = StringUtils.hasText(breed) ? breed : "宠物";
        return switch (type) {
            case PostType.LOST -> "寻" + safeBreed + "启事";
            case PostType.FOUND -> "招领" + safeBreed;
            case PostType.BREED -> safeBreed + "相亲";
            default -> safeBreed;
        };
    }

    private long calcRewardFee(long rewardCents) {
        BigDecimal rate = sysConfigService.getDecimal("reward.service.rate", new BigDecimal("0.08"));
        long cap = sysConfigService.getLong("reward.service.cap.cents", 8000);
        long fee = BigDecimal.valueOf(rewardCents).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValue();
        return Math.min(fee, cap);
    }

    private void maybeSavePet(long userId, Post post, PostCreateRequest request) {
        if (!Boolean.TRUE.equals(request.getSaveAsPet()) || request.getPetId() != null) {
            return;
        }
        Pet pet = new Pet();
        pet.setUserId(userId);
        pet.setName(StringUtils.hasText(request.getBreed()) ? request.getBreed().substring(0, Math.min(10, request.getBreed().length())) : "宝贝");
        pet.setPetType(post.getPetType());
        pet.setBreed(post.getBreed());
        pet.setGender(StringUtils.hasText(post.getGender()) ? post.getGender() : "UNKNOWN");
        pet.setSterilized(StringUtils.hasText(post.getSterilized()) ? post.getSterilized() : "UNKNOWN");
        pet.setVaccineStatus(post.getVaccineStatus());
        pet.setChipFeature(post.getChipFeature());
        pet.setAvatarUrl(post.getCoverUrl());
        petMapper.insert(pet);
        Post update = new Post();
        update.setId(post.getId());
        update.setPetId(pet.getId());
        postMapper.updateById(update);
        post.setPetId(pet.getId());
    }

    private void incrementView(long postId) {
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Post>()
                .setSql("view_count = view_count + 1")
                .eq("id", postId));
    }

    public PostCardVO toCard(Post post) {
        PostCardVO vo = new PostCardVO();
        vo.setId(post.getId());
        vo.setType(post.getType());
        vo.setTitle(post.getTitle());
        vo.setPetType(post.getPetType());
        vo.setBreed(post.getBreed());
        vo.setDistrict(post.getDistrict());
        vo.setEventTime(post.getEventTime());
        vo.setRewardAmount(RewardStatus.FROZEN.equals(post.getRewardStatus()) || RewardStatus.PAID.equals(post.getRewardStatus()) ? post.getRewardAmount() : 0L);
        vo.setCoverUrl(post.getCoverUrl());
        vo.setStatus(post.getStatus());
        vo.setTopExpireTime(post.getTopExpireTime());
        vo.setClueCount(post.getClueCount());
        vo.setDescription(post.getDescription());
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }

    private FoundVO toFound(Post post) {
        FoundVO vo = new FoundVO();
        vo.setId(post.getId());
        vo.setType(post.getType());
        vo.setTitle(post.getTitle());
        vo.setPetType(post.getPetType());
        vo.setBreed(post.getBreed());
        vo.setDistrict(post.getDistrict());
        vo.setEventTime(post.getEventTime());
        vo.setFoundTime(post.getFoundTime());
        vo.setRewardAmount(post.getRewardAmount());
        vo.setCoverUrl(post.getCoverUrl());
        if (post.getEventTime() != null && post.getFoundTime() != null) {
            vo.setFindDays(Math.max(0, ChronoUnit.DAYS.between(post.getEventTime(), post.getFoundTime())));
        }
        var summary = splitService.summary(post.getId(), null, post.getUserId());
        if (summary != null) {
            vo.setParticipantCount(summary.getParticipantCount());
            vo.setTotalAmount(summary.getTotalAmount());
        }
        return vo;
    }

    private String nickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getNickname()) ? "宠友" : user.getNickname();
    }

    private List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private int normalize(int value) {
        return Math.min(Math.max(value, 1), 100);
    }
}
