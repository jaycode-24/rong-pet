package com.jaycode.rongpet.module.claim.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.claim.dto.ClaimSubmitRequest;
import com.jaycode.rongpet.module.claim.dto.ClaimVO;
import com.jaycode.rongpet.module.claim.entity.Claim;
import com.jaycode.rongpet.module.claim.error.ClaimErrorCode;
import com.jaycode.rongpet.module.claim.mapper.ClaimMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.PostType;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.security.SensitiveWordService;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认领申请服务：结构化申请、先审后发、拾宠人确认、归还完成。
 * 认领沟通期间双方仅可见脱敏联系方式，完整号码不落前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimMapper claimMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final PostService postService;
    private final CryptoService cryptoService;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;
    private final SensitiveWordService sensitiveWordService;

    // ------------------------------------------------------------------ 申请人

    @Transactional
    public ClaimVO submit(long userId, long postId, ClaimSubmitRequest request) {
        Post post = requirePost(postId);
        if (!PostType.FOUND.equals(post.getType()) || !PostStatus.visible(post.getStatus())) {
            throw new BizException(ClaimErrorCode.CLAIM_POST_NOT_FOUND);
        }
        if (post.getUserId() == userId) {
            throw new BizException(ClaimErrorCode.CLAIM_SELF);
        }
        if (request.getMaterials() == null || request.getMaterials().size() < 2) {
            throw new BizException(ClaimErrorCode.CLAIM_MATERIAL_REQUIRED);
        }
        // 敏感词/导流拦截（PRD FR-4.3）
        if (sensitiveWordService.containsSensitive(request.getDescription())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "认领描述包含违规信息，请修改后再提交");
        }
        long existing = claimMapper.selectCount(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getPostId, postId)
                .eq(Claim::getUserId, userId)
                .in(Claim::getStatus, "PENDING_AUDIT", "APPROVED", "CONFIRMED"));
        if (existing > 0) {
            throw new BizException(ClaimErrorCode.CLAIM_DUPLICATE);
        }
        long total = claimMapper.selectCount(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getPostId, postId));
        if (total >= sysConfigService.getLong("claim.max.per.post", 20)) {
            throw new BizException(ClaimErrorCode.CLAIM_LIMIT);
        }

        Claim claim = new Claim();
        claim.setPostId(postId);
        claim.setUserId(userId);
        claim.setDescription(request.getDescription());
        claim.setMaterials(JSONUtil.toJsonStr(request.getMaterials()));
        claim.setStatus("PENDING_AUDIT");
        claimMapper.insert(claim);
        incrementClaimCount(postId);

        messageService.send(userId, MessageService.TYPE_ORDER, "认领申请已提交",
                "您的认领申请已提交审核，预计4小时内完成审核", "CLAIM", String.valueOf(claim.getId()));
        return toVO(claim, post, userId);
    }

    public PageResult<ClaimVO> mine(long userId, int pageNum, int pageSize) {
        Page<Claim> page = claimMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Claim>()
                        .eq(Claim::getUserId, userId)
                        .orderByDesc(Claim::getCreateTime));
        return PageResult.of(page.getRecords().stream()
                        .map(c -> toVO(c, postMapper.selectById(c.getPostId()), userId)).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 拾宠人查看自己帖子下已通过审核的认领申请；申请人只能看自己的申请 */
    public List<ClaimVO> listForPost(long viewerId, long postId) {
        Post post = requirePost(postId);
        boolean owner = post.getUserId() == viewerId;
        LambdaQueryWrapper<Claim> wrapper = new LambdaQueryWrapper<Claim>()
                .eq(Claim::getPostId, postId)
                .orderByDesc(Claim::getCreateTime);
        if (owner) {
            wrapper.in(Claim::getStatus, "APPROVED", "CONFIRMED", "REFUSED");
        } else {
            wrapper.eq(Claim::getUserId, viewerId);
        }
        return claimMapper.selectList(wrapper).stream()
                .map(c -> toVO(c, post, viewerId)).toList();
    }

    // ------------------------------------------------------------------ 拾宠人

    @Transactional
    public void confirm(long userId, long claimId) {
        Claim claim = requireClaim(claimId);
        Post post = requirePost(claim.getPostId());
        if (post.getUserId() != userId) {
            throw new BizException(ClaimErrorCode.CLAIM_ONLY_OWNER);
        }
        if (!"APPROVED".equals(claim.getStatus())) {
            throw new BizException(ClaimErrorCode.CLAIM_STATUS_INVALID);
        }
        long confirmed = claimMapper.selectCount(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getPostId, post.getId())
                .eq(Claim::getStatus, "CONFIRMED"));
        if (confirmed > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "该帖子已确认其他认领申请");
        }
        Claim update = new Claim();
        update.setId(claimId);
        update.setStatus("CONFIRMED");
        update.setConfirmTime(LocalDateTime.now());
        claimMapper.updateById(update);

        // 其余已通过审核的申请自动拒绝，避免多方同时认领
        claimMapper.update(null, new UpdateWrapper<Claim>()
                .set("status", "REFUSED")
                .set("audit_remark", "拾宠人已确认其他认领申请")
                .eq("post_id", post.getId())
                .eq("status", "APPROVED"));

        messageService.send(claim.getUserId(), MessageService.TYPE_ORDER, "认领已确认",
                "拾宠人已确认您的认领申请，双方可查看脱敏联系方式并线下交接",
                "CLAIM", String.valueOf(claimId));
        messageService.send(post.getUserId(), MessageService.TYPE_ORDER, "认领确认成功",
                "您已确认一位失主的认领申请，请尽快完成线下交接", "POST", String.valueOf(post.getId()));
    }

    @Transactional
    public void refuse(long userId, long claimId, String reason) {
        Claim claim = requireClaim(claimId);
        Post post = requirePost(claim.getPostId());
        if (post.getUserId() != userId) {
            throw new BizException(ClaimErrorCode.CLAIM_ONLY_OWNER);
        }
        if (!"APPROVED".equals(claim.getStatus())) {
            throw new BizException(ClaimErrorCode.CLAIM_STATUS_INVALID);
        }
        Claim update = new Claim();
        update.setId(claimId);
        update.setStatus("REFUSED");
        update.setAuditRemark(reason);
        claimMapper.updateById(update);
        messageService.send(claim.getUserId(), MessageService.TYPE_ORDER, "认领未通过",
                "拾宠人未确认您的认领申请" + (StringUtils.hasText(reason) ? "：" + reason : ""),
                "CLAIM", String.valueOf(claimId));
    }

    /** 拾宠人确认归还：需已确认的认领申请，帖子转已完成并进入已找回列表 */
    @Transactional
    public void confirmReturn(long userId, long postId) {
        Post post = requirePost(postId);
        if (!PostType.FOUND.equals(post.getType())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        if (post.getUserId() != userId) {
            throw new BizException(PostErrorCode.POST_NOT_OWNER);
        }
        if (PostStatus.COMPLETED.equals(post.getStatus())) {
            return;
        }
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PostErrorCode.POST_STATUS_INVALID);
        }
        Claim confirmed = claimMapper.selectOne(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getPostId, postId)
                .eq(Claim::getStatus, "CONFIRMED")
                .last("limit 1"));
        if (confirmed == null) {
            throw new BizException(ClaimErrorCode.CLAIM_NOT_CONFIRMED);
        }
        Post update = new Post();
        update.setId(postId);
        update.setStatus(PostStatus.COMPLETED);
        update.setFoundTime(LocalDateTime.now());
        postMapper.updateById(update);
        postService.log(postId, userId, "USER", "CONFIRM_RETURN", post.getStatus(), PostStatus.COMPLETED, null);

        messageService.send(confirmed.getUserId(), MessageService.TYPE_ORDER, "宠物已完成归还",
                "帖子《" + post.getTitle() + "》已标记归还完成，感谢您的善意", "POST", String.valueOf(postId));
        messageService.send(post.getUserId(), MessageService.TYPE_ORDER, "归还已完成",
                "帖子《" + post.getTitle() + "》已进入已找回列表", "POST", String.valueOf(postId));
    }

    // ------------------------------------------------------------------ 后台审核

    public PageResult<ClaimVO> adminPage(String status, Long postId, int pageNum, int pageSize) {
        Page<Claim> page = claimMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Claim>()
                        .eq(StringUtils.hasText(status), Claim::getStatus, status)
                        .eq(postId != null, Claim::getPostId, postId)
                        .orderByAsc(Claim::getCreateTime));
        return PageResult.of(page.getRecords().stream()
                        .map(c -> toVO(c, postMapper.selectById(c.getPostId()), null)).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public void audit(long adminId, long claimId, boolean approve, String remark) {
        Claim claim = requireClaim(claimId);
        if (!"PENDING_AUDIT".equals(claim.getStatus())) {
            throw new BizException(ClaimErrorCode.CLAIM_STATUS_INVALID);
        }
        Claim update = new Claim();
        update.setId(claimId);
        update.setStatus(approve ? "APPROVED" : "REJECTED");
        update.setAuditRemark(remark);
        claimMapper.updateById(update);

        Post post = requirePost(claim.getPostId());
        if (approve) {
            messageService.send(claim.getUserId(), MessageService.TYPE_AUDIT, "认领申请审核通过",
                    "您的认领申请已通过审核，等待拾宠人核验", "CLAIM", String.valueOf(claimId));
            messageService.send(post.getUserId(), MessageService.TYPE_ORDER, "收到新的认领申请",
                    "招领帖《" + post.getTitle() + "》收到一份已通过审核的认领申请，请及时核验",
                    "CLAIM", String.valueOf(claimId));
        } else {
            messageService.send(claim.getUserId(), MessageService.TYPE_AUDIT, "认领申请未通过",
                    "原因：" + (StringUtils.hasText(remark) ? remark : "材料不足或信息不符"),
                    "CLAIM", String.valueOf(claimId));
        }
    }

    // ------------------------------------------------------------------ 内部

    private Claim requireClaim(long claimId) {
        Claim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(ClaimErrorCode.CLAIM_NOT_FOUND);
        }
        return claim;
    }

    private Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void incrementClaimCount(long postId) {
        postMapper.update(null, new UpdateWrapper<Post>()
                .setSql("claim_count = claim_count + 1")
                .eq("id", postId));
    }

    private ClaimVO toVO(Claim claim, Post post, Long viewerId) {
        ClaimVO vo = new ClaimVO();
        vo.setId(claim.getId());
        vo.setPostId(claim.getPostId());
        vo.setPostTitle(post == null ? null : post.getTitle());
        vo.setUserId(claim.getUserId());
        vo.setNickname(nickname(claim.getUserId()));
        vo.setDescription(claim.getDescription());
        vo.setMaterials(claim.getMaterials() == null ? List.of()
                : JSONUtil.toList(claim.getMaterials(), String.class));
        vo.setStatus(claim.getStatus());
        vo.setAuditRemark(claim.getAuditRemark());
        vo.setConfirmTime(claim.getConfirmTime());
        vo.setCreateTime(claim.getCreateTime());
        if (post != null && viewerId != null && "CONFIRMED".equals(claim.getStatus())) {
            if (post.getUserId().equals(viewerId)) {
                vo.setContactMasked(null);
            } else if (claim.getUserId().equals(viewerId)) {
                vo.setContactMasked(null);
            }
        }
        return vo;
    }

    private String maskUserPhone(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getPhoneEnc())) {
            return null;
        }
        return CryptoService.maskPhone(cryptoService.decrypt(user.getPhoneEnc()));
    }

    private String nickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getNickname()) ? "宠友" : user.getNickname();
    }
}
