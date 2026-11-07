package com.jaycode.rongpet.module.account.service;

import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.dto.UpdateProfileRequest;
import com.jaycode.rongpet.module.account.dto.UserVO;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.error.AccountErrorCode;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.feeding.entity.FeedingRequest;
import com.jaycode.rongpet.module.feeding.mapper.FeedingRequestMapper;
import com.jaycode.rongpet.module.pay.constant.PayStatus;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.RewardStatus;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.security.StpUserUtil;
import com.jaycode.rongpet.security.crypto.CryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 小程序用户资料服务。当前登录用户才能查询/更新自己，身份取自会话而非请求参数。
 */
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final UserMapper userMapper;
    private final AppUserVOAssembler assembler;
    private final CryptoService cryptoService;
    private final PostMapper postMapper;
    private final PayOrderMapper payOrderMapper;
    private final FeedingRequestMapper feedingRequestMapper;

    public UserVO me() {
        User user = requireCurrentUser();
        UserVO vo = assembler.toVO(user);
        vo.setMaskedPhone(CryptoService.maskPhone(cryptoService.decrypt(user.getPhoneEnc())));
        vo.setRealNameVerified(StringUtils.hasText(user.getRealNameEnc()));
        return vo;
    }

    /** 大额悬赏实名补充：姓名/身份证仅加密落库，不返回明文 */
    public void submitRealName(com.jaycode.rongpet.module.account.dto.RealNameRequest request) {
        User current = requireCurrentUser();
        User update = new User();
        update.setId(current.getId());
        update.setRealNameEnc(cryptoService.encrypt(request.getRealName()));
        update.setIdCardEnc(cryptoService.encrypt(request.getIdCard()));
        update.setIdCardHmac(cryptoService.hmacIndex(request.getIdCard()));
        userMapper.updateById(update);
    }

    /** 资料更新按字段跳过空值，昵称内容安全审核接入前先做长度兜底 */
    public UserVO updateProfile(UpdateProfileRequest request) {
        User current = requireCurrentUser();
        User update = new User();
        update.setId(current.getId());
        if (request != null) {
            if (StringUtils.hasText(request.getNickname())) {
                if (request.getNickname().length() > 64) {
                    throw new BizException(CommonErrorCode.BAD_REQUEST, "昵称长度需在64字以内");
                }
                update.setNickname(request.getNickname());
            }
            if (StringUtils.hasText(request.getAvatarUrl())) {
                update.setAvatarUrl(request.getAvatarUrl());
            }
        }
        userMapper.updateById(update);
        return me();
    }

    private User requireCurrentUser() {
        long userId = StpUserUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(AccountErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 账号注销（NFR-4 数据合规）：存在未结资金/在途托管/进行中喂养请求时禁止注销。
     * 注销后账号不可登录，个人敏感信息脱敏，业务数据按法定要求留存用于对账与举证。
     */
    @Transactional
    public void cancel() {
        User current = requireCurrentUser();
        long userId = current.getId();

        // 1. 在途悬赏托管必须先结束（找回分账或到期退款）
        long frozenReward = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getRewardStatus, RewardStatus.FROZEN));
        if (frozenReward > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "存在托管中的悬赏，请等待分账或退款完成后再注销");
        }
        // 2. 未完成支付单（含待支付/退款中）
        long openOrders = payOrderMapper.selectCount(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getUserId, userId)
                .in(PayOrder::getStatus, PayStatus.CREATED, PayStatus.REFUNDING));
        if (openOrders > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "存在未完成的支付或退款订单，请处理后再注销");
        }
        // 3. 进行中的喂养请求
        long openFeeding = feedingRequestMapper.selectCount(new LambdaQueryWrapper<FeedingRequest>()
                .eq(FeedingRequest::getUserId, userId)
                .in(FeedingRequest::getStatus, "PENDING", "CONTACTED"));
        if (openFeeding > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "存在进行中的喂养请求，请关闭后再注销");
        }
        // 4. 待审核/展示中的帖子需先下架或结束
        long activePosts = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .in(Post::getStatus, PostStatus.PENDING_AUDIT, PostStatus.SHOWING,
                        PostStatus.REWARD_FROZEN, PostStatus.WAIT_SCORE, PostStatus.SPLITTING));
        if (activePosts > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "存在进行中的帖子，请下架或等待结束后再注销");
        }

        User update = new User();
        update.setId(userId);
        update.setStatus(UserStatus.CANCELLED);
        update.setCancelTime(LocalDateTime.now());
        // 个人资料脱敏：昵称/头像清空，手机号与实名密文保留用于法定留存，展示层不再返回
        update.setNickname("已注销用户");
        update.setAvatarUrl(null);
        userMapper.updateById(update);
        StpUserUtil.logout();
    }
}
