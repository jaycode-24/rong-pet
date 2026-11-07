package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.experience.entity.RewardDispute;
import com.jaycode.rongpet.module.experience.mapper.*;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.DisputeItem;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RewardDisputeService {
    private final RewardDisputeMapper disputes;
    private final ExperienceLockMapper locks;
    private final PostMapper posts;
    private final ClueMapper clues;
    private final SysConfigService config;
    private final MessageService messages;
    private static final Set<String> FOUND_STATES = Set.of("WAIT_SCORE", "SPLITTING", "COMPLETED");

    @Transactional
    public void submit(long user, long postId, String content, List<String> evidence) {
        String encoded = EvidenceCodec.encode(evidence);
        Post post = lockedFrozen(postId);
        if (clues.selectCount(new LambdaQueryWrapper<Clue>().eq(Clue::getPostId, postId).eq(Clue::getUserId, user)) == 0)
            throw new BizException(CommonErrorCode.BAD_REQUEST, "仅该帖线索提供者可发起找回争议");
        if (disputes.selectCount(new LambdaQueryWrapper<RewardDispute>().eq(RewardDispute::getPostId, postId)
                .eq(RewardDispute::getUserId, user).eq(RewardDispute::getStatus, "PENDING")) > 0)
            throw new BizException(CommonErrorCode.CONFLICT, "已有待核验争议，请等待处理");
        RewardDispute item = new RewardDispute();
        item.setPostId(postId);
        item.setUserId(user);
        item.setContent(content);
        item.setEvidence(encoded);
        item.setStatus("PENDING");
        try {
            disputes.insert(item);
        } catch (DuplicateKeyException error) {
            throw new BizException(CommonErrorCode.CONFLICT, "已有待核验争议，请等待处理");
        }
        messages.send(post.getUserId(), MessageService.TYPE_FUND, "悬赏存在找回争议", "线索提供者已提交证据，退款暂停，等待人工核验", "POST", String.valueOf(postId));
    }

    @Transactional
    public void ensureRefundAllowed(long postId) {
        Post post = locks.lockPost(postId);
        if (post == null) throw new BizException(CommonErrorCode.NOT_FOUND, "帖子不存在");
        if (FOUND_STATES.contains(post.getStatus())) throw new BizException(CommonErrorCode.CONFLICT, "已找回或正在分配，不可退款");
        if (openDisputes(postId) > 0) throw new BizException(CommonErrorCode.CONFLICT, "争议未结清，暂停退款");
        long valid = clues.selectCount(new LambdaQueryWrapper<Clue>().eq(Clue::getPostId, postId).eq(Clue::getStatus, "VALID"));
        long approved = disputes.selectCount(new LambdaQueryWrapper<RewardDispute>().eq(RewardDispute::getPostId, postId).eq(RewardDispute::getStatus, "REFUND_APPROVED"));
        if (valid > 0 && approved == 0) throw new BizException(CommonErrorCode.CONFLICT, "存在有效线索，需人工核验后批准退款");
        if (post.getRewardExpireTime() == null) throw new BizException(CommonErrorCode.CONFLICT, "悬赏到期时间缺失，需人工核验");
        if (LocalDateTime.now().isBefore(post.getRewardExpireTime().plusHours(Math.max(0, config.getInt("reward.refund.objection.hours", 72)))))
            throw new BizException(CommonErrorCode.CONFLICT, "尚处于到期后异议窗口，暂不可退款");
    }

    @Transactional
    public void approveRefund(long admin, long postId, String reason) {
        Post post = lockedFrozen(postId);
        if (FOUND_STATES.contains(post.getStatus())) throw new BizException(CommonErrorCode.CONFLICT, "已确认找回，不允许按未找回退款");
        if (openDisputes(postId) > 0) throw new BizException(CommonErrorCode.CONFLICT, "请先逐条处理未结争议");
        RewardDispute item = new RewardDispute();
        item.setPostId(postId);
        item.setUserId(post.getUserId());
        item.setContent("人工退款核验");
        item.setEvidence("[]");
        item.setStatus("REFUND_APPROVED");
        item.setDecision(reason);
        item.setAdminId(admin);
        disputes.insert(item);
        messages.send(post.getUserId(), MessageService.TYPE_FUND, "退款核验完成", reason, "POST", String.valueOf(postId));
    }

    @Transactional
    public void confirmFound(long admin, long id, List<Long> clueIds, String reason) {
        RewardDispute initial = require(id);
        Post post = lockedFrozen(initial.getPostId());
        RewardDispute dispute = requireOpen(id);
        if (FOUND_STATES.contains(post.getStatus())) throw new BizException(CommonErrorCode.CONFLICT, "已进入找回分配流程");
        Set<Long> ids = new HashSet<>(clueIds);
        if (ids.contains(null) || ids.isEmpty() || ids.size() > config.getInt("clue.valid.max", 5))
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请选择1至5条经核实的有效线索");
        List<Clue> selected = clues.selectBatchIds(ids);
        if (selected.size() != ids.size() || selected.stream().anyMatch(clue -> !Objects.equals(clue.getPostId(), post.getId())))
            throw new BizException(CommonErrorCode.BAD_REQUEST, "线索不属于该帖");
        for (Clue previous : clues.selectList(new LambdaQueryWrapper<Clue>().eq(Clue::getPostId, post.getId()).eq(Clue::getStatus, "VALID"))) {
            if (!ids.contains(previous.getId())) {
                previous.setStatus("INVALID");
                changed(clues.updateById(previous));
            }
        }
        for (Clue clue : selected) {
            clue.setStatus("VALID");
            changed(clues.updateById(clue));
        }
        post.setStatus("WAIT_SCORE");
        post.setFoundTime(LocalDateTime.now());
        post.setFoundNote(reason);
        post.setFoundProof(EvidenceCodec.encode(EvidenceCodec.decode(dispute.getEvidence())));
        changed(posts.updateById(post));
        dispute.setStatus("UPHELD");
        dispute.setDecision(reason);
        dispute.setAdminId(admin);
        changed(disputes.updateById(dispute));
        notifyDecision(dispute, post, "人工核验已找回", reason);
        selected.stream().map(Clue::getUserId).distinct().filter(user -> !Objects.equals(user, dispute.getUserId()) && !Objects.equals(user, post.getUserId()))
                .forEach(user -> messages.send(user, MessageService.TYPE_FUND, "人工核验已找回", "请等待评分及人工转付", "POST", String.valueOf(post.getId())));
    }

    public List<DisputeItem> list(Long user, int page) {
        ExperienceService.validatePage(page);
        return disputes.selectPage(new Page<>(page, ExperienceService.PAGE_SIZE, false), new LambdaQueryWrapper<RewardDispute>()
                .eq(user != null, RewardDispute::getUserId, user).orderByDesc(RewardDispute::getCreateTime).orderByDesc(RewardDispute::getId))
                .getRecords().stream().map(d -> new DisputeItem(d.getId(), d.getPostId(), d.getUserId(), d.getContent(), EvidenceCodec.decode(d.getEvidence()), d.getStatus(), d.getDecision(), d.getCreateTime())).toList();
    }

    @Transactional
    public void resolve(long admin, long id, String decision, String reason) {
        RewardDispute initial = require(id);
        Post post = locks.lockPost(initial.getPostId());
        if (post == null) throw new BizException(CommonErrorCode.NOT_FOUND, "帖子不存在");
        RewardDispute dispute = requireOpen(id);
        if (!Set.of("REJECTED", "UPHELD", "REFUND_APPROVED").contains(decision)) throw new BizException(CommonErrorCode.BAD_REQUEST, "处理结论不合法");
        if (FOUND_STATES.contains(post.getStatus()) && "REFUND_APPROVED".equals(decision))
            throw new BizException(CommonErrorCode.CONFLICT, "已找回不可批准未找回退款");
        dispute.setStatus(decision);
        dispute.setDecision(reason);
        dispute.setAdminId(admin);
        changed(disputes.updateById(dispute));
        notifyDecision(dispute, post, "找回争议处理结果", reason);
    }

    private void notifyDecision(RewardDispute dispute, Post post, String title, String reason) {
        String text = "处理结论：" + dispute.getStatus() + "；" + reason;
        messages.send(dispute.getUserId(), MessageService.TYPE_FUND, title, text, "REWARD_DISPUTE", String.valueOf(dispute.getId()));
        if (!Objects.equals(dispute.getUserId(), post.getUserId()))
            messages.send(post.getUserId(), MessageService.TYPE_FUND, title, text, "POST", String.valueOf(post.getId()));
    }

    private Post lockedFrozen(long id) {
        Post post = locks.lockPost(id);
        if (post == null || !"FROZEN".equals(post.getRewardStatus())) throw new BizException(CommonErrorCode.CONFLICT, "悬赏已结清或不存在");
        return post;
    }

    private long openDisputes(long post) {
        return disputes.selectCount(new LambdaQueryWrapper<RewardDispute>().eq(RewardDispute::getPostId, post).in(RewardDispute::getStatus, "PENDING", "UPHELD"));
    }

    private RewardDispute require(long id) {
        RewardDispute dispute = disputes.selectById(id);
        if (dispute == null) throw new BizException(CommonErrorCode.NOT_FOUND, "争议不存在");
        return dispute;
    }

    private RewardDispute requireOpen(long id) {
        RewardDispute dispute = require(id);
        if (!Set.of("PENDING", "UPHELD").contains(dispute.getStatus())) throw new BizException(CommonErrorCode.CONFLICT, "争议已结案，请刷新");
        return dispute;
    }

    private void changed(int rows) {
        if (rows != 1) throw new BizException(CommonErrorCode.CONFLICT, "记录已更新，请刷新后重试");
    }
}
