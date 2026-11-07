package com.jaycode.rongpet.module.split.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.clue.dto.ClueScoreRequest;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.error.ClueErrorCode;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.fund.service.FundFlowService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.RewardStatus;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostLogMapper;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.split.dto.SplitPlanVO;
import com.jaycode.rongpet.module.split.dto.SplitSummaryVO;
import com.jaycode.rongpet.module.split.entity.SplitItem;
import com.jaycode.rongpet.module.split.entity.SplitPlan;
import com.jaycode.rongpet.module.split.mapper.SplitItemMapper;
import com.jaycode.rongpet.module.split.mapper.SplitPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评分分账服务：评分 → 计算 → 人工核验 → 按人汇总转付（系统按线索拆分留痕）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SplitService {

    private final SplitPlanMapper splitPlanMapper;
    private final SplitItemMapper splitItemMapper;
    private final ClueMapper clueMapper;
    private final PostMapper postMapper;
    private final PostLogMapper postLogMapper;
    private final UserMapper userMapper;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;
    private final FundFlowService fundFlowService;

    // ------------------------------------------------------------------ 评分

    @Transactional
    public void submitScores(long userId, long postId, ClueScoreRequest request) {
        Post post = requirePost(postId);
        if (post.getUserId() != userId) {
            throw new BizException(PostErrorCode.POST_NOT_OWNER);
        }
        if (!PostStatus.WAIT_SCORE.equals(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        List<Clue> valid = validClues(postId);
        Map<Long, ClueScoreRequest.Item> submitted = new LinkedHashMap<>();
        for (ClueScoreRequest.Item item : request.getItems()) {
            if (item.getClueId() == null || item.getScore() == null || item.getScore() < 1 || item.getScore() > 5) {
                throw new BizException(ClueErrorCode.CLUE_SCORE_INVALID);
            }
            submitted.put(item.getClueId(), item);
        }
        for (Clue clue : valid) {
            if (!submitted.containsKey(clue.getId())) {
                throw new BizException(ClueErrorCode.CLUE_SCORE_INCOMPLETE);
            }
        }
        if (submitted.size() != valid.size()) {
            throw new BizException(ClueErrorCode.CLUE_SCORE_INCOMPLETE);
        }
        for (Clue clue : valid) {
            ClueScoreRequest.Item item = submitted.get(clue.getId());
            Clue update = new Clue();
            update.setId(clue.getId());
            update.setScore(item.getScore());
            update.setScoreComment(item.getComment());
            update.setScoreSource("PUBLISHER");
            clueMapper.updateById(update);
            clue.setScore(item.getScore());
        }
        createPlan(post, valid);
    }

    /** 评分逾期：按默认规则均分（每条线索 1 分权重），由定时任务调用 */
    @Transactional
    public void autoDefault(Post post) {
        if (!PostStatus.WAIT_SCORE.equals(post.getStatus())) {
            return;
        }
        List<Clue> valid = validClues(post.getId());
        if (valid.isEmpty()) {
            log.warn("帖子 {} 待评分但没有有效线索，跳过默认分账", post.getId());
            return;
        }
        for (Clue clue : valid) {
            Clue update = new Clue();
            update.setId(clue.getId());
            update.setScore(1);
            update.setScoreSource("SYSTEM_DEFAULT");
            clueMapper.updateById(update);
            clue.setScore(1);
        }
        createPlan(post, valid);
    }

    private void createPlan(Post post, List<Clue> valid) {
        if (splitPlanMapper.selectCount(new LambdaQueryWrapper<SplitPlan>()
                .eq(SplitPlan::getPostId, post.getId())) > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "该帖子分账方案已生成");
        }
        long base = post.getRewardAmount() == null ? 0L : post.getRewardAmount();
        if (base <= 0) {
            throw new BizException(PostErrorCode.REWARD_NOT_PAID);
        }
        List<SplitCalculator.Input> inputs = valid.stream()
                .map(c -> new SplitCalculator.Input(c.getId(), c.getScore()))
                .toList();
        Map<Long, Long> amounts = SplitCalculator.calculate(base, inputs);

        SplitPlan plan = new SplitPlan();
        plan.setPostId(post.getId());
        plan.setTotalAmount(base);
        plan.setStatus("WAIT_REVIEW");
        plan.setReviewRequired(base >= sysConfigService.getLong("split.review.cents", 300_000) ? 1 : 0);
        splitPlanMapper.insert(plan);

        for (Clue clue : valid) {
            SplitItem item = new SplitItem();
            item.setPlanId(plan.getId());
            item.setPostId(post.getId());
            item.setClueId(clue.getId());
            item.setUserId(clue.getUserId());
            item.setScore(clue.getScore());
            item.setAmount(amounts.getOrDefault(clue.getId(), 0L));
            item.setPayStatus("UNPAID");
            splitItemMapper.insert(item);
        }
        Post update = new Post();
        update.setId(post.getId());
        update.setStatus(PostStatus.SPLITTING);
        update.setRewardStatus(RewardStatus.SPLITTING);
        postMapper.updateById(update);

        valid.forEach(c -> messageService.send(c.getUserId(), MessageService.TYPE_FUND, "分账处理中",
                "帖子《" + post.getTitle() + "》的分账方案已生成，等待平台核验转付", "POST", String.valueOf(post.getId())));
        log(post.getId(), null, "SYSTEM", "SPLIT_CREATED", PostStatus.WAIT_SCORE, PostStatus.SPLITTING,
                "本金=" + base + "分，线索数=" + valid.size());
    }

    // ------------------------------------------------------------------ 后台核验 / 转付

    public PageResult<SplitPlanVO> adminPage(String status, int pageNum, int pageSize) {
        Page<SplitPlan> page = splitPlanMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SplitPlan>()
                        .eq(StringUtils.hasText(status), SplitPlan::getStatus, status)
                        .orderByAsc(SplitPlan::getCreateTime));
        List<SplitPlanVO> records = page.getRecords().stream().map(this::toPlanVO).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public void approve(long planId, long adminId) {
        SplitPlan plan = requirePlan(planId);
        if (!"WAIT_REVIEW".equals(plan.getStatus())) {
            throw new BizException(CommonErrorCode.CONFLICT, "该方案已核验或已转付");
        }
        SplitPlan update = new SplitPlan();
        update.setId(planId);
        update.setStatus("APPROVED");
        update.setApproveTime(LocalDateTime.now());
        splitPlanMapper.updateById(update);
        log(plan.getPostId(), adminId, "ADMIN", "SPLIT_APPROVED", null, null, "planId=" + planId);
    }

    /** 按收款人汇总转付：系统仍按线索逐条拆分留痕，单帖不超过 5 笔（有效线索上限保证） */
    @Transactional
    public void transfer(long planId, long adminId, String adminName, String voucherNo) {
        SplitPlan plan = requirePlan(planId);
        if (!"APPROVED".equals(plan.getStatus())) {
            throw new BizException(CommonErrorCode.CONFLICT, "请先核验分账方案");
        }
        List<SplitItem> items = splitItemMapper.selectList(new LambdaQueryWrapper<SplitItem>()
                .eq(SplitItem::getPlanId, planId));
        for (SplitItem item : items) {
            SplitItem update = new SplitItem();
            update.setId(item.getId());
            update.setPayStatus("PAID");
            update.setPaidTime(LocalDateTime.now());
            splitItemMapper.updateById(update);

            Clue clue = new Clue();
            clue.setId(item.getClueId());
            clue.setPayStatus("PAID");
            clue.setShareAmount(item.getAmount());
            clueMapper.updateById(clue);

            fundFlowService.record(item.getUserId(), "SPLIT", String.valueOf(item.getClueId()),
                    "OUT", item.getAmount(), "SUCCESS", voucherNo, adminName, "悬赏分账转付");
            messageService.send(item.getUserId(), MessageService.TYPE_FUND, "分账已转付",
                    "您分得 " + yuan(item.getAmount()) + " 元，请注意查收", "POST", String.valueOf(plan.getPostId()));
        }
        SplitPlan update = new SplitPlan();
        update.setId(planId);
        update.setStatus("PAID");
        update.setPaidTime(LocalDateTime.now());
        splitPlanMapper.updateById(update);

        Post post = requirePost(plan.getPostId());
        Post postUpdate = new Post();
        postUpdate.setId(post.getId());
        postUpdate.setStatus(PostStatus.COMPLETED);
        postUpdate.setRewardStatus(RewardStatus.PAID);
        postMapper.updateById(postUpdate);
        messageService.send(post.getUserId(), MessageService.TYPE_FUND, "分账结果已公示",
                "帖子《" + post.getTitle() + "》已完成分账并进入已找回列表", "POST", String.valueOf(post.getId()));
        log(post.getId(), adminId, "ADMIN", "SPLIT_PAID", PostStatus.SPLITTING, PostStatus.COMPLETED,
                "凭证=" + voucherNo);
    }

    // ------------------------------------------------------------------ 公示与查询

    /** 分账汇总；明细按当事人可见性裁剪，对外仅汇总口径 */
    public SplitSummaryVO summary(long postId, Long viewerId, long ownerId) {
        SplitPlan plan = splitPlanMapper.selectOne(new LambdaQueryWrapper<SplitPlan>()
                .eq(SplitPlan::getPostId, postId));
        if (plan == null) {
            return null;
        }
        List<SplitItem> items = splitItemMapper.selectList(new LambdaQueryWrapper<SplitItem>()
                .eq(SplitItem::getPlanId, plan.getId()));
        SplitSummaryVO vo = new SplitSummaryVO();
        Set<Long> users = new HashSet<>();
        long total = 0;
        for (SplitItem item : items) {
            users.add(item.getUserId());
            total += item.getAmount() == null ? 0 : item.getAmount();
        }
        vo.setParticipantCount(users.size());
        vo.setTotalAmount(total);

        boolean viewerIsOwner = viewerId != null && viewerId == ownerId;
        List<SplitSummaryVO.Item> visible = new ArrayList<>();
        for (SplitItem item : items) {
            boolean mine = viewerId != null && viewerId.equals(item.getUserId());
            if (viewerIsOwner || mine || publicDetail(item.getClueId())) {
                SplitSummaryVO.Item vi = new SplitSummaryVO.Item();
                vi.setClueId(item.getClueId());
                vi.setNickname(maskNickname(item.getUserId()));
                vi.setScore(item.getScore());
                vi.setAmount(item.getAmount());
                vi.setMine(mine);
                visible.add(vi);
            }
        }
        vo.setItems(visible);
        return vo;
    }

    @Transactional
    public void toggleDetailPublic(long userId, long clueId, boolean open) {
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new BizException(ClueErrorCode.CLUE_NOT_FOUND);
        }
        if (!clue.getUserId().equals(userId)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "仅线索提供者本人可设置公开");
        }
        Clue update = new Clue();
        update.setId(clueId);
        update.setDetailPublic(open ? 1 : 0);
        clueMapper.updateById(update);
    }

    /** 评分逾期候选：确认找回超过评分时限仍待评分 */
    public List<Post> overdueScoreCandidates(LocalDateTime cutoff, int limit) {
        return postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.WAIT_SCORE)
                .lt(Post::getFoundTime, cutoff)
                .last("limit " + limit));
    }

    public List<SplitItem> itemsOf(long planId) {
        return splitItemMapper.selectList(new LambdaQueryWrapper<SplitItem>()
                .eq(SplitItem::getPlanId, planId));
    }

    // ------------------------------------------------------------------ 内部

    private List<Clue> validClues(long postId) {
        return clueMapper.selectList(new LambdaQueryWrapper<Clue>()
                .eq(Clue::getPostId, postId)
                .eq(Clue::getStatus, "VALID")
                .orderByAsc(Clue::getCreateTime));
    }

    private boolean publicDetail(long clueId) {
        Clue clue = clueMapper.selectById(clueId);
        return clue != null && clue.getDetailPublic() != null && clue.getDetailPublic() == 1;
    }

    private SplitPlanVO toPlanVO(SplitPlan plan) {
        SplitPlanVO vo = new SplitPlanVO();
        vo.setId(plan.getId());
        vo.setPostId(plan.getPostId());
        Post post = postMapper.selectById(plan.getPostId());
        vo.setPostTitle(post == null ? null : post.getTitle());
        vo.setTotalAmount(plan.getTotalAmount());
        vo.setStatus(plan.getStatus());
        vo.setReviewRequired(plan.getReviewRequired() != null && plan.getReviewRequired() == 1);
        vo.setCreateTime(plan.getCreateTime());
        List<SplitSummaryVO.Item> items = new ArrayList<>();
        for (SplitItem item : itemsOf(plan.getId())) {
            SplitSummaryVO.Item vi = new SplitSummaryVO.Item();
            vi.setClueId(item.getClueId());
            vi.setNickname(maskNickname(item.getUserId()));
            vi.setScore(item.getScore());
            vi.setAmount(item.getAmount());
            items.add(vi);
        }
        vo.setItems(items);
        return vo;
    }

    private String maskNickname(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getNickname())) {
            return "宠友";
        }
        String nickname = user.getNickname();
        return nickname.length() <= 1 ? nickname : nickname.charAt(0) + "**";
    }

    private SplitPlan requirePlan(long planId) {
        SplitPlan plan = splitPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "分账方案不存在");
        }
        return plan;
    }

    private Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void log(long postId, Long adminId, String type, String action,
                     String from, String to, String detail) {
        com.jaycode.rongpet.module.post.entity.PostLog entry = new com.jaycode.rongpet.module.post.entity.PostLog();
        entry.setPostId(postId);
        entry.setOperatorId(adminId);
        entry.setOperatorType(type);
        entry.setAction(action);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setDetail(detail);
        postLogMapper.insert(entry);
    }

    private String yuan(long cents) {
        return java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
