package com.jaycode.rongpet.module.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.fund.service.FundFlowService;
import com.jaycode.rongpet.module.gratuity.entity.Gratuity;
import com.jaycode.rongpet.module.gratuity.mapper.GratuityMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.pay.constant.PayBizType;
import com.jaycode.rongpet.module.pay.constant.PayStatus;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.error.PayErrorCode;
import com.jaycode.rongpet.module.pay.gateway.PayGateway;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.PostType;
import com.jaycode.rongpet.module.post.constant.RewardStatus;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.error.PostErrorCode;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.module.post.service.TopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付编排服务。
 * <ul>
 *   <li>下单：悬赏 / 置顶 / 感谢金，全部落支付单，业务单号唯一</li>
 *   <li>入账：微信回调与 mock 通道统一走 {@link #markPaid}，按支付单状态条件更新保证幂等</li>
 *   <li>退款：原路退回并冲减业务状态，流水留痕</li>
 *   <li>到期退款：悬赏到期由定时任务扫描并原路退回本金与服务费</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final PayOrderMapper payOrderMapper;
    private final com.jaycode.rongpet.module.experience.mapper.PetAvatarTaskMapper avatarTasks;
    private final PostMapper postMapper;
    private final GratuityMapper gratuityMapper;
    private final UserMapper userMapper;
    private final PayGateway payGateway;
    private final TopService topService;
    private final PostService postService;
    private final FundFlowService fundFlowService;
    private final SysConfigService sysConfigService;
    private final MessageService messageService;
    private final com.jaycode.rongpet.module.experience.RewardDisputeService rewardDisputeService;

    // ------------------------------------------------------------------ 下单

    @Transactional
    public PayInitVO createRewardPay(long userId, long postId) {
        Post post = requirePost(postId);
        if (post.getUserId() != userId) {
            throw new BizException(PostErrorCode.POST_NOT_OWNER);
        }
        if (!PostType.LOST.equals(post.getType())) {
            throw new BizException(PayErrorCode.REWARD_NOT_PAYABLE);
        }
        if (!PostStatus.SHOWING.equals(post.getStatus())
                && !PostStatus.REWARD_FROZEN.equals(post.getStatus())) {
            throw new BizException(PayErrorCode.REWARD_NOT_PAYABLE);
        }
        if (RewardStatus.FROZEN.equals(post.getRewardStatus())) {
            throw new BizException(PayErrorCode.REWARD_ALREADY_PAID);
        }
        if (post.getRewardAmount() == null || post.getRewardAmount() <= 0) {
            throw new BizException(PayErrorCode.REWARD_NOT_PAYABLE);
        }
        User payer = userMapper.selectById(userId);
        if (payer == null || UserStatus.BANNED.equals(payer.getStatus())
                || UserStatus.FROZEN.equals(payer.getStatus())
                || UserStatus.LIMIT_TRADE.equals(payer.getStatus())) {
            throw new BizException(PayErrorCode.USER_FORBIDDEN);
        }
        long largeReview = sysConfigService.getLong("reward.large.review.cents", 500_000);
        if (post.getRewardAmount() >= largeReview && !StringUtils.hasText(payer.getRealNameEnc())) {
            throw new BizException(PayErrorCode.REAL_NAME_REQUIRED);
        }
        long existing = payOrderMapper.selectCount(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getBizType, PayBizType.REWARD)
                .eq(PayOrder::getBizNo, String.valueOf(postId))
                .eq(PayOrder::getStatus, PayStatus.PAID));
        if (existing > 0) {
            throw new BizException(PayErrorCode.REWARD_ALREADY_PAID);
        }
        long fee = post.getRewardFee() == null ? calcRewardFee(post.getRewardAmount()) : post.getRewardFee();
        PayOrder order = createOrder(userId, PayBizType.REWARD, String.valueOf(postId),
                post.getRewardAmount(), fee, post.getRewardAmount() + fee);
        return initiate(order);
    }

    @Transactional
    public PayInitVO createTopPay(long userId, long postId) {
        Post post = requirePost(postId);
        if (post.getUserId() != userId) {
            throw new BizException(PostErrorCode.POST_NOT_OWNER);
        }
        if (!PostStatus.visible(post.getStatus())) {
            throw new BizException(PayErrorCode.TOP_NOT_PAYABLE);
        }
        if (post.getTopExpireTime() != null && post.getTopExpireTime().isAfter(LocalDateTime.now())) {
            throw new BizException(PostErrorCode.TOP_DUPLICATE);
        }
        topService.checkCanTop(postId);
        long price = topService.paidPrice(post);
        PayOrder order = createOrder(userId, PayBizType.TOP, String.valueOf(postId), price, 0L, price);
        return initiate(order);
    }

    /** 感谢金：失主支付金额，平台抽成从金额内扣除（不额外向失主收取） */
    @Transactional
    public PayInitVO createGratuityPay(long userId, long gratuityId, long amount) {
        PayOrder order = createOrder(userId, PayBizType.GRATUITY, String.valueOf(gratuityId),
                amount, 0L, amount);
        Gratuity update = new Gratuity();
        update.setId(gratuityId);
        update.setOrderNo(order.getOutTradeNo());
        gratuityMapper.updateById(update);
        return initiate(order);
    }

    /**
     * 创建支付单（不发起支付），供各业务复用。金额单位分。
     */
    @Transactional
    public PayOrder createOrder(long userId, String bizType, String bizNo,
                                long amount, long fee, long total) {
        PayOrder order = new PayOrder();
        order.setOutTradeNo(generateOutTradeNo());
        order.setBizType(bizType);
        order.setBizNo(bizNo);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setFee(fee);
        order.setTotal(total);
        order.setStatus(PayStatus.CREATED);
        order.setChannel(payGateway.mock() ? "MOCK" : "WXPAY");
        payOrderMapper.insert(order);
        return order;
    }

    /** 发起支付：mock 直接入账；微信返回 JSAPI 调起参数 */
    @Transactional
    public PayInitVO initiate(PayOrder order) {
        User user = userMapper.selectById(order.getUserId());
        Map<String, String> params = payGateway.pay(order,
                user == null ? null : user.getOpenId());
        PayInitVO vo = new PayInitVO();
        vo.setOutTradeNo(order.getOutTradeNo());
        vo.setTotal(order.getTotal());
        vo.setPayParams(params);
        if (payGateway.mock()) {
            markPaid(order, "MOCK-" + order.getOutTradeNo(), order.getTotal());
            vo.setPaid(true);
            vo.setStatus(PayStatus.PAID);
        } else {
            vo.setPaid(false);
            vo.setStatus(PayStatus.CREATED);
        }
        return vo;
    }

    // ------------------------------------------------------------------ 入账（幂等）

    /** 微信回调统一入口：按商户单号入账，重复通知直接返回成功 */
    @Transactional
    public boolean onCallbackPaid(String outTradeNo, String transactionId, long paidTotal) {
        PayOrder order = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo));
        if (order == null) {
            log.warn("支付回调未找到支付单 outTradeNo={}", outTradeNo);
            return false;
        }
        if (paidTotal != order.getTotal()) {
            log.error("支付回调金额不一致 outTradeNo={}, expect={}, actual={}",
                    outTradeNo, order.getTotal(), paidTotal);
            return false;
        }
        return markPaid(order, transactionId, paidTotal);
    }

    /**
     * 入账：条件更新 CREATED → PAID 保证并发与重复回调只入账一次。
     * 业务侧状态更新同样做幂等判断，避免回调重放造成重复冻结/重复置顶。
     */
    @Transactional
    public boolean markPaid(PayOrder order, String transactionId, long paidTotal) {
        int updated = payOrderMapper.update(null, new UpdateWrapper<PayOrder>()
                .set("status", PayStatus.PAID)
                .set("transaction_id", transactionId)
                .set("paid_time", LocalDateTime.now())
                .eq("id", order.getId())
                .eq("status", PayStatus.CREATED));
        if (updated == 0) {
            log.info("支付单已处理，忽略重复入账 outTradeNo={}", order.getOutTradeNo());
            return true;
        }
        fundFlowService.record(order.getUserId(), order.getBizType(), order.getBizNo(),
                "IN", paidTotal, "SUCCESS", transactionId, null, "支付入账");

        switch (order.getBizType()) {
            case "AI_AVATAR" -> avatarTasks.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.jaycode.rongpet.module.experience.entity.PetAvatarTask>().eq("order_no", order.getOutTradeNo()).eq("status", "WAIT_PAY").set("status", "QUEUED").set("update_time", LocalDateTime.now()).setSql("version=version+1"));
            case PayBizType.REWARD -> onRewardPaid(order);
            case PayBizType.TOP -> onTopPaid(order);
            case PayBizType.GRATUITY -> onGratuityPaid(order);
            default -> log.warn("未知支付业务类型 {}", order.getBizType());
        }
        return true;
    }

    private void onRewardPaid(PayOrder order) {
        Post post = requirePost(Long.parseLong(order.getBizNo()));
        if (RewardStatus.FROZEN.equals(post.getRewardStatus())) {
            return;
        }
        int days = post.getRewardDays() == null ? 7 : post.getRewardDays();
        LocalDateTime expire = LocalDateTime.now().plusDays(days);
        Post update = new Post();
        update.setId(post.getId());
        update.setStatus(PostStatus.REWARD_FROZEN);
        update.setRewardStatus(RewardStatus.FROZEN);
        update.setRewardExpireTime(expire);
        update.setExpireTime(expire);
        postMapper.updateById(update);
        postService.log(post.getId(), order.getUserId(), "USER", "REWARD_PAID",
                post.getStatus(), PostStatus.REWARD_FROZEN, "支付单=" + order.getOutTradeNo());
        messageService.send(post.getUserId(), MessageService.TYPE_FUND, "悬赏支付成功",
                "悬赏本金 " + yuan(order.getAmount()) + " 元、服务费 " + yuan(order.getFee())
                        + " 元已托管，有效期 " + days + " 天", "POST", String.valueOf(post.getId()));
    }

    private void onTopPaid(PayOrder order) {
        Post post = requirePost(Long.parseLong(order.getBizNo()));
        topService.activatePaid(post.getId(), topService.paidDays(post), order.getOutTradeNo());
    }

    private void onGratuityPaid(PayOrder order) {
        Gratuity gratuity = gratuityMapper.selectOne(new LambdaQueryWrapper<Gratuity>()
                .eq(Gratuity::getOrderNo, order.getOutTradeNo()));
        if (gratuity == null) {
            gratuity = gratuityMapper.selectById(Long.parseLong(order.getBizNo()));
        }
        if (gratuity == null || "FROZEN".equals(gratuity.getStatus()) || "PAID".equals(gratuity.getStatus())) {
            return;
        }
        Gratuity update = new Gratuity();
        update.setId(gratuity.getId());
        update.setStatus("FROZEN");
        update.setOrderNo(order.getOutTradeNo());
        update.setPaidTime(LocalDateTime.now());
        gratuityMapper.updateById(update);

        Post post = requirePost(gratuity.getPostId());
        if (!PostStatus.COMPLETED.equals(post.getStatus())) {
            Post postUpdate = new Post();
            postUpdate.setId(post.getId());
            postUpdate.setStatus(PostStatus.COMPLETED);
            postUpdate.setFoundTime(post.getFoundTime() == null ? LocalDateTime.now() : post.getFoundTime());
            postUpdate.setGratuityAmount(gratuity.getAmount());
            postMapper.updateById(postUpdate);
            postService.log(post.getId(), order.getUserId(), "USER", "GRATUITY_PAID",
                    post.getStatus(), PostStatus.COMPLETED, "感谢金托管");
        }
        messageService.send(post.getUserId(), MessageService.TYPE_FUND, "收到感谢金",
                "失主已托管感谢金 " + yuan(gratuity.getAmount()) + " 元，待平台核验后转付",
                "POST", String.valueOf(post.getId()));
    }

    // ------------------------------------------------------------------ 退款

    @Transactional
    public void refundByOrderNo(String outTradeNo, String reason, String operator) {
        PayOrder order = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo));
        if (order == null) {
            throw new BizException(PayErrorCode.ORDER_NOT_FOUND);
        }
        refund(order, reason, operator);
    }

    @Transactional
    public void refund(PayOrder order, String reason, String operator) {
        if (!PayStatus.PAID.equals(order.getStatus())) {
            throw new BizException(PayErrorCode.REFUND_NOT_ALLOWED);
        }
        ensureRefundable(order);
        long refundAmount = order.getTotal();
        try {
            payGateway.refund(order, refundAmount, reason);
        } catch (Exception e) {
            // 退款失败进入异常队列，累计重试次数；连续 3 次标记 FAILED 转人工线下处理（PRD FR-5.2.10）
            markRefundFailed(order, e);
            throw new BizException(PayErrorCode.WXPAY_ERROR, "退款失败，已进入异常队列等待重试");
        }
        int updated = payOrderMapper.update(null, new UpdateWrapper<PayOrder>()
                .set("status", PayStatus.REFUNDED)
                .set("refund_no", "RF" + order.getOutTradeNo())
                .set("refund_amount", refundAmount)
                .set("refund_time", LocalDateTime.now())
                .set("fail_reason", reason)
                .set("refund_retry_count", 0)
                .eq("id", order.getId())
                .eq("status", PayStatus.PAID));
        if (updated == 0) {
            throw new BizException(PayErrorCode.REFUND_NOT_ALLOWED);
        }
        order.setStatus(PayStatus.REFUNDED);
        fundFlowService.record(order.getUserId(), "REFUND", order.getBizNo(),
                "OUT", refundAmount, "SUCCESS", "RF" + order.getOutTradeNo(), operator, reason);

        switch (order.getBizType()) {
            case "AI_AVATAR" -> avatarTasks.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.jaycode.rongpet.module.experience.entity.PetAvatarTask>().eq("order_no", order.getOutTradeNo()).eq("status", "FAILED").set("status", "REFUNDED").set("update_time", LocalDateTime.now()).setSql("version=version+1"));
            case PayBizType.REWARD -> onRewardRefunded(order, reason);
            case PayBizType.TOP -> topService.deactivateByOrder(order.getOutTradeNo());
            case PayBizType.GRATUITY -> onGratuityRefunded(order, reason);
            default -> {
            }
        }
    }

    /** 退款失败留痕：重试 +1，达到 3 次转 FAILED 由管理员每日巡检线下处理 */
    private void markRefundFailed(PayOrder order, Exception cause) {
        int retry = (order.getRefundRetryCount() == null ? 0 : order.getRefundRetryCount()) + 1;
        UpdateWrapper<PayOrder> wrapper = new UpdateWrapper<PayOrder>()
                .eq("id", order.getId())
                .set("refund_retry_count", retry)
                .set("fail_reason", "退款失败第" + retry + "次："
                        + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
        if (retry >= 3) {
            wrapper.set("status", PayStatus.FAILED);
        }
        payOrderMapper.update(null, wrapper);
        order.setRefundRetryCount(retry);
        fundFlowService.record(order.getUserId(), "REFUND", order.getBizNo(),
                "OUT", order.getTotal(), "FAILED", null, "SYSTEM",
                "退款失败第" + retry + "次" + (retry >= 3 ? "，转人工处理" : "，等待重试"));
        log.error("退款失败 orderNo={}, retry={}", order.getOutTradeNo(), retry, cause);
    }

    /** 异常队列：退款失败待重试/转人工的支付单（FAILED 或 PAID 且有失败计数） */
    public PageResult<PayOrder> refundFailedPage(int pageNum, int pageSize) {
        Page<PayOrder> page = payOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PayOrder>()
                        .and(w -> w.eq(PayOrder::getStatus, PayStatus.FAILED)
                                .or(x -> x.gt(PayOrder::getRefundRetryCount, 0)
                                        .eq(PayOrder::getStatus, PayStatus.PAID)))
                        .orderByDesc(PayOrder::getUpdateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 人工重试退款：FAILED 单重新走退款，成功后由 refund() 统一冲减业务并清零计数 */
    @Transactional
    public void retryRefund(long orderId, String operator) {
        PayOrder order = findById(orderId);
        if (!PayStatus.FAILED.equals(order.getStatus())
                && (order.getRefundRetryCount() == null || order.getRefundRetryCount() <= 0)) {
            throw new BizException(PayErrorCode.REFUND_NOT_ALLOWED);
        }
        // FAILED 单需先回到 PAID 才能复用退款状态机
        if (PayStatus.FAILED.equals(order.getStatus())) {
            payOrderMapper.update(null, new UpdateWrapper<PayOrder>()
                    .set("status", PayStatus.PAID)
                    .eq("id", orderId)
                    .eq("status", PayStatus.FAILED));
            order.setStatus(PayStatus.PAID);
        }
        refund(order, "人工重试退款", operator);
    }

    /**
     * 定时任务自动重试退款失败但未达 3 次的订单（PRD FR-5.2.10）。
     * 单条失败不阻断整批；达到 3 次的 FAILED 单不在此处理，等待管理员每日巡检线下处理。
     */
    @Transactional
    public int retryFailedRefunds() {
        List<PayOrder> candidates = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .gt(PayOrder::getRefundRetryCount, 0)
                .lt(PayOrder::getRefundRetryCount, 3)
                .eq(PayOrder::getStatus, PayStatus.PAID)
                .last("limit 100"));
        int retried = 0;
        for (PayOrder order : candidates) {
            try {
                refund(order, "系统自动重试退款", "SYSTEM");
                retried++;
            } catch (Exception e) {
                log.error("退款自动重试失败 orderNo={}", order.getOutTradeNo(), e);
            }
        }
        return retried;
    }

    private void ensureRefundable(PayOrder order) {
        if ("AI_AVATAR".equals(order.getBizType())) {
            Long failed=avatarTasks.selectCount(new LambdaQueryWrapper<com.jaycode.rongpet.module.experience.entity.PetAvatarTask>().eq(com.jaycode.rongpet.module.experience.entity.PetAvatarTask::getOrderNo, order.getOutTradeNo()).eq(com.jaycode.rongpet.module.experience.entity.PetAvatarTask::getStatus, "FAILED"));
            if(failed==null||failed==0)throw new BizException(PayErrorCode.REFUND_NOT_ALLOWED);
        }
        if (PayBizType.REWARD.equals(order.getBizType())) {
            rewardDisputeService.ensureRefundAllowed(Long.parseLong(order.getBizNo()));
            Post post = requirePost(Long.parseLong(order.getBizNo()));
            if (!RewardStatus.FROZEN.equals(post.getRewardStatus())) {
                throw new BizException(PayErrorCode.REFUND_NOT_ALLOWED);
            }
        } else if (PayBizType.GRATUITY.equals(order.getBizType())) {
            Gratuity gratuity = gratuityMapper.selectById(Long.parseLong(order.getBizNo()));
            if (gratuity == null || (!"FROZEN".equals(gratuity.getStatus())
                    && !"CREATED".equals(gratuity.getStatus()))) {
                throw new BizException(PayErrorCode.REFUND_NOT_ALLOWED);
            }
        }
    }

    private void onRewardRefunded(PayOrder order, String reason) {
        Post post = requirePost(Long.parseLong(order.getBizNo()));
        if (RewardStatus.REFUNDED.equals(post.getRewardStatus())) {
            return;
        }
        Post update = new Post();
        update.setId(post.getId());
        update.setRewardStatus(RewardStatus.REFUNDED);
        if (!PostStatus.COMPLETED.equals(post.getStatus())) {
            update.setStatus(PostStatus.EXPIRED);
        }
        postMapper.updateById(update);
        postService.log(post.getId(), null, "SYSTEM", "REWARD_REFUNDED",
                post.getStatus(), update.getStatus(), reason);
        messageService.send(post.getUserId(), MessageService.TYPE_FUND, "悬赏已退款",
                "悬赏本金与服务费共 " + yuan(order.getTotal()) + " 元已原路退回", "POST", order.getBizNo());
    }

    private void onGratuityRefunded(PayOrder order, String reason) {
        Gratuity gratuity = gratuityMapper.selectById(Long.parseLong(order.getBizNo()));
        if (gratuity == null) {
            return;
        }
        Gratuity update = new Gratuity();
        update.setId(gratuity.getId());
        update.setStatus("REFUNDED");
        update.setRefundReason(reason);
        gratuityMapper.updateById(update);
        messageService.send(order.getUserId(), MessageService.TYPE_FUND, "感谢金已退回",
                "感谢金 " + yuan(order.getAmount()) + " 元已原路退回", "POST", String.valueOf(gratuity.getPostId()));
    }

    // ------------------------------------------------------------------ 定时任务

    /** 悬赏到期退款：本金与服务费全额原路退回 */
    @Transactional
    public int expireRewardPosts() {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.REWARD_FROZEN)
                .eq(Post::getRewardStatus, RewardStatus.FROZEN)
                .isNotNull(Post::getRewardExpireTime)
                .lt(Post::getRewardExpireTime, LocalDateTime.now())
                .last("limit 100"));
        int refunded = 0;
        for (Post post : posts) {
            PayOrder order = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getBizType, PayBizType.REWARD)
                    .eq(PayOrder::getBizNo, String.valueOf(post.getId()))
                    .eq(PayOrder::getStatus, PayStatus.PAID)
                    .orderByDesc(PayOrder::getPaidTime)
                    .last("limit 1"));
            if (order == null) {
                log.error("悬赏到期但未找到已支付订单，转人工处理 postId={}", post.getId());
                continue;
            }
            try {
                refund(order, "悬赏到期自动退款", "SYSTEM");
                refunded++;
            } catch (Exception e) {
                log.error("悬赏到期退款失败 postId={}, orderNo={}", post.getId(), order.getOutTradeNo(), e);
            }
        }
        return refunded;
    }

    /** 到期前 24 小时提醒 */
    @Transactional
    public int remindRewardExpiring() {
        LocalDateTime now = LocalDateTime.now();
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.REWARD_FROZEN)
                .eq(Post::getRewardStatus, RewardStatus.FROZEN)
                .between(Post::getRewardExpireTime, now, now.plusHours(24))
                .last("limit 200"));
        for (Post post : posts) {
            messageService.send(post.getUserId(), MessageService.TYPE_FUND, "悬赏即将到期",
                    "帖子《" + post.getTitle() + "》悬赏将在24小时内到期，可进入帖子详情延长一次",
                    "POST", String.valueOf(post.getId()));
        }
        return posts.size();
    }

    /** 支付超时 30 分钟关闭未支付订单 */
    @Transactional
    public int closeTimeoutOrders() {
        return payOrderMapper.update(null, new UpdateWrapper<PayOrder>()
                .set("status", PayStatus.CLOSED)
                .eq("status", PayStatus.CREATED)
                .lt("create_time", LocalDateTime.now().minusMinutes(30)));
    }

    // ------------------------------------------------------------------ 查询

    public PageResult<PayOrder> adminPage(String status, String bizType, Long userId, int pageNum, int pageSize) {
        Page<PayOrder> page = payOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PayOrder>()
                        .eq(StringUtils.hasText(status), PayOrder::getStatus, status)
                        .eq(StringUtils.hasText(bizType), PayOrder::getBizType, bizType)
                        .eq(userId != null, PayOrder::getUserId, userId)
                        .orderByDesc(PayOrder::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<PayOrder> mine(long userId, String bizType, int pageNum, int pageSize) {
        Page<PayOrder> page = payOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getUserId, userId)
                        .eq(StringUtils.hasText(bizType), PayOrder::getBizType, bizType)
                        .orderByDesc(PayOrder::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PayOrder findByOutTradeNo(String outTradeNo) {
        return payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo));
    }

    public PayOrder findById(long id) {
        PayOrder order = payOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(PayErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    // ------------------------------------------------------------------ 内部

    private long calcRewardFee(long rewardCents) {
        BigDecimal rate = sysConfigService.getDecimal("reward.service.rate", new BigDecimal("0.08"));
        long cap = sysConfigService.getLong("reward.service.cap.cents", 8000);
        long fee = BigDecimal.valueOf(rewardCents).multiply(rate)
                .setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return Math.min(fee, cap);
    }

    private Post requirePost(long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(PostErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private String generateOutTradeNo() {
        return "RP" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String yuan(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
