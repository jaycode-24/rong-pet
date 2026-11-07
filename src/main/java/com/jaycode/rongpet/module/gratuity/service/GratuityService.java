package com.jaycode.rongpet.module.gratuity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.claim.entity.Claim;
import com.jaycode.rongpet.module.claim.mapper.ClaimMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.fund.service.FundFlowService;
import com.jaycode.rongpet.module.gratuity.dto.GratuityPayRequest;
import com.jaycode.rongpet.module.gratuity.dto.GratuityVO;
import com.jaycode.rongpet.module.gratuity.entity.Gratuity;
import com.jaycode.rongpet.module.gratuity.error.GratuityErrorCode;
import com.jaycode.rongpet.module.gratuity.mapper.GratuityMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 感谢金服务：失主（已确认认领的申请人）支付托管 → 管理员核验 → 扣除平台抽成后转付拾宠人。
 * 免抽成期内费率为 0。感谢金支付前不可转付，转付前可协商退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GratuityService {

    private final GratuityMapper gratuityMapper;
    private final ClaimMapper claimMapper;
    private final PostMapper postMapper;
    private final CryptoService cryptoService;
    private final SysConfigService sysConfigService;
    private final PayService payService;
    private final FundFlowService fundFlowService;
    private final MessageService messageService;

    // ------------------------------------------------------------------ 失主支付

    @Transactional
    public PayInitVO createAndPay(long userId, long postId, GratuityPayRequest request) {
        Post post = requirePost(postId);
        if (!"FOUND".equals(post.getType())) {
            throw new BizException(GratuityErrorCode.NOT_CONFIRMED_CLAIM);
        }
        Claim claim = claimMapper.selectOne(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getPostId, postId)
                .eq(Claim::getUserId, userId)
                .eq(Claim::getStatus, "CONFIRMED")
                .last("limit 1"));
        if (claim == null) {
            throw new BizException(GratuityErrorCode.NOT_CONFIRMED_CLAIM);
        }
        long amount = request.getAmount() == null ? 0 : request.getAmount();
        long min = 100L;
        long max = sysConfigService.getLong("gratuity.max.cents", 1_000_000);
        if (amount < min || amount > max) {
            throw new BizException(GratuityErrorCode.AMOUNT_INVALID,
                    "感谢金需在 " + yuan(min) + " - " + yuan(max) + " 元之间");
        }
        Gratuity existing = gratuityMapper.selectOne(new LambdaQueryWrapper<Gratuity>()
                .eq(Gratuity::getPostId, postId)
                .in(Gratuity::getStatus, "FROZEN", "PAID")
                .last("limit 1"));
        if (existing != null) {
            throw new BizException(GratuityErrorCode.ALREADY_PAID);
        }
        long fee = calcFee(amount);
        long net = amount - fee;

        Gratuity gratuity = gratuityMapper.selectOne(new LambdaQueryWrapper<Gratuity>()
                .eq(Gratuity::getPostId, postId)
                .eq(Gratuity::getStatus, "CREATED")
                .last("limit 1"));
        if (gratuity == null) {
            gratuity = new Gratuity();
            gratuity.setPostId(postId);
            gratuity.setPayerId(userId);
            gratuity.setStatus("CREATED");
        }
        gratuity.setAmount(amount);
        gratuity.setFee(fee);
        gratuity.setNetAmount(net);
        gratuity.setPayeeAccountEnc(cryptoService.encrypt(request.getPayeeAccount()));
        gratuity.setPayeeAccountHmac(cryptoService.hmacIndex(request.getPayeeAccount()));
        gratuity.setStatus("CREATED");
        if (gratuity.getId() == null) {
            gratuityMapper.insert(gratuity);
        } else {
            gratuityMapper.updateById(gratuity);
        }
        return payService.createGratuityPay(userId, gratuity.getId(), amount);
    }

    public GratuityVO myGratuity(long userId, long postId) {
        Gratuity gratuity = gratuityMapper.selectOne(new LambdaQueryWrapper<Gratuity>()
                .eq(Gratuity::getPostId, postId)
                .eq(Gratuity::getPayerId, userId)
                .orderByDesc(Gratuity::getCreateTime)
                .last("limit 1"));
        return gratuity == null ? null : toVO(gratuity);
    }

    // ------------------------------------------------------------------ 后台

    public PageResult<GratuityVO> adminPage(String status, Long postId, int pageNum, int pageSize) {
        Page<Gratuity> page = gratuityMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Gratuity>()
                        .eq(StringUtils.hasText(status), Gratuity::getStatus, status)
                        .eq(postId != null, Gratuity::getPostId, postId)
                        .orderByAsc(Gratuity::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 管理员核验后转付：金额为扣除抽成后的实收 */
    @Transactional
    public void transfer(long gratuityId, long adminId, String adminName, String voucherNo) {
        Gratuity gratuity = require(gratuityId);
        if (!"FROZEN".equals(gratuity.getStatus())) {
            throw new BizException(GratuityErrorCode.STATUS_INVALID);
        }
        Post post = requirePost(gratuity.getPostId());
        Gratuity update = new Gratuity();
        update.setId(gratuityId);
        update.setStatus("PAID");
        update.setPaidTime(LocalDateTime.now());
        gratuityMapper.updateById(update);

        fundFlowService.record(post.getUserId(), "GRATUITY", String.valueOf(post.getId()),
                "OUT", gratuity.getNetAmount(), "SUCCESS", voucherNo, adminName, "感谢金转付拾宠人");
        if (gratuity.getFee() != null && gratuity.getFee() > 0) {
            fundFlowService.record(null, "GRATUITY", String.valueOf(post.getId()),
                    "IN", gratuity.getFee(), "SUCCESS", voucherNo, adminName, "感谢金平台抽成");
        }
        messageService.send(post.getUserId(), MessageService.TYPE_FUND, "感谢金已转付",
                "感谢金 " + yuan(gratuity.getAmount()) + " 元已转付，实收 "
                        + yuan(gratuity.getNetAmount()) + " 元", "POST", String.valueOf(post.getId()));
        messageService.send(gratuity.getPayerId(), MessageService.TYPE_FUND, "感谢金已完成转付",
                "您对帖子《" + post.getTitle() + "》的感谢金已转付拾宠人", "POST", String.valueOf(post.getId()));
    }

    @Transactional
    public void refund(long gratuityId, long adminId, String reason) {
        Gratuity gratuity = require(gratuityId);
        if ("REFUNDED".equals(gratuity.getStatus())) {
            return;
        }
        if (!"FROZEN".equals(gratuity.getStatus()) && !"CREATED".equals(gratuity.getStatus())) {
            throw new BizException(GratuityErrorCode.STATUS_INVALID);
        }
        if ("FROZEN".equals(gratuity.getStatus()) && StringUtils.hasText(gratuity.getOrderNo())) {
            payService.refundByOrderNo(gratuity.getOrderNo(), reason, "ADMIN-" + adminId);
        }
        Gratuity update = new Gratuity();
        update.setId(gratuityId);
        update.setStatus("REFUNDED");
        update.setRefundReason(reason);
        gratuityMapper.updateById(update);
    }

    // ------------------------------------------------------------------ 内部

    private long calcFee(long amount) {
        if (sysConfigService.gratuityFreePeriod()) {
            return 0L;
        }
        BigDecimal rate = sysConfigService.getDecimal("gratuity.rate", new BigDecimal("0.10"));
        return BigDecimal.valueOf(amount).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private Gratuity require(long id) {
        Gratuity gratuity = gratuityMapper.selectById(id);
        if (gratuity == null) {
            throw new BizException(GratuityErrorCode.NOT_FOUND);
        }
        return gratuity;
    }

    private Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private GratuityVO toVO(Gratuity gratuity) {
        GratuityVO vo = new GratuityVO();
        vo.setId(gratuity.getId());
        vo.setPostId(gratuity.getPostId());
        Post post = postMapper.selectById(gratuity.getPostId());
        vo.setPostTitle(post == null ? null : post.getTitle());
        vo.setPayerId(gratuity.getPayerId());
        vo.setAmount(gratuity.getAmount());
        vo.setFee(gratuity.getFee());
        vo.setNetAmount(gratuity.getNetAmount());
        vo.setPayeeAccountMasked(mask(gratuity.getPayeeAccountEnc()));
        vo.setOrderNo(gratuity.getOrderNo());
        vo.setStatus(gratuity.getStatus());
        vo.setRefundReason(gratuity.getRefundReason());
        vo.setPaidTime(gratuity.getPaidTime());
        vo.setCreateTime(gratuity.getCreateTime());
        return vo;
    }

    private String mask(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            String plain = cryptoService.decrypt(encrypted);
            if (plain.length() <= 4) {
                return "****";
            }
            return "****" + plain.substring(plain.length() - 4);
        } catch (Exception e) {
            return "****";
        }
    }

    private String yuan(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
