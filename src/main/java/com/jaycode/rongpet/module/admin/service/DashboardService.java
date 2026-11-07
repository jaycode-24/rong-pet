package com.jaycode.rongpet.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.admin.dto.DashboardVO;
import com.jaycode.rongpet.module.claim.entity.Claim;
import com.jaycode.rongpet.module.claim.mapper.ClaimMapper;
import com.jaycode.rongpet.module.feeder.entity.FeederApply;
import com.jaycode.rongpet.module.feeder.entity.FeederProfile;
import com.jaycode.rongpet.module.feeder.mapper.FeederApplyMapper;
import com.jaycode.rongpet.module.feeder.mapper.FeederProfileMapper;
import com.jaycode.rongpet.module.pay.constant.PayBizType;
import com.jaycode.rongpet.module.pay.constant.PayStatus;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.PostType;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.report.entity.Report;
import com.jaycode.rongpet.module.report.mapper.ReportMapper;
import com.jaycode.rongpet.module.split.entity.SplitPlan;
import com.jaycode.rongpet.module.split.mapper.SplitPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台数据看板（PRD 7.2）：用户、内容、供给、交易、找回与风险指标。
 * 金额字段单位为分；所有统计实时读取业务表，口径与页面明细一致。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final ClaimMapper claimMapper;
    private final FeederProfileMapper feederProfileMapper;
    private final FeederApplyMapper feederApplyMapper;
    private final PayOrderMapper payOrderMapper;
    private final SplitPlanMapper splitPlanMapper;
    private final ReportMapper reportMapper;

    public DashboardVO overview() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        DashboardVO vo = new DashboardVO();

        // 用户
        vo.setTotalUsers(userMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setTodayNewUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, startOfDay)));
        vo.setLimitedUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .in(User::getStatus, UserStatus.LIMIT_POST, UserStatus.LIMIT_TRADE)));
        vo.setBannedUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .in(User::getStatus, UserStatus.BANNED, UserStatus.FROZEN)));

        // 内容
        vo.setTotalPosts(postMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setTodayNewPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .ge(Post::getCreateTime, startOfDay)));
        vo.setPendingAuditPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.PENDING_AUDIT)));
        vo.setShowingPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .in(Post::getStatus, PostStatus.SHOWING, PostStatus.REWARD_FROZEN)));
        vo.setCompletedPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.COMPLETED)));
        vo.setPendingClaims(claimMapper.selectCount(new LambdaQueryWrapper<Claim>()
                .eq(Claim::getStatus, "PENDING_AUDIT")));
        vo.setPendingReports(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, "PENDING")));

        // 供给
        vo.setCertifiedFeeders(feederProfileMapper.selectCount(new LambdaQueryWrapper<FeederProfile>()
                .eq(FeederProfile::getStatus, "ACTIVE")
                .gt(FeederProfile::getCertExpireTime, now)));
        vo.setActiveFeeders(feederProfileMapper.selectCount(new LambdaQueryWrapper<FeederProfile>()
                .eq(FeederProfile::getStatus, "ACTIVE")));
        vo.setPendingFeederApplies(feederApplyMapper.selectCount(new LambdaQueryWrapper<FeederApply>()
                .eq(FeederApply::getStatus, "PENDING")));

        // 交易
        vo.setRewardFrozenAmount(sum(postMapper.selectObjs(new QueryWrapper<Post>()
                .select("COALESCE(SUM(reward_amount), 0)")
                .eq("status", PostStatus.REWARD_FROZEN))));
        vo.setRewardPaidAmount(sum(splitPlanMapper.selectObjs(new QueryWrapper<SplitPlan>()
                .select("COALESCE(SUM(total_amount), 0)")
                .eq("status", "PAID"))));
        vo.setServiceFeeIncome(sum(payOrderMapper.selectObjs(new QueryWrapper<PayOrder>()
                .select("COALESCE(SUM(fee), 0)")
                .eq("status", PayStatus.PAID)
                .eq("biz_type", PayBizType.REWARD))));
        vo.setGratuityFeeIncome(sum(payOrderMapper.selectObjs(new QueryWrapper<PayOrder>()
                .select("COALESCE(SUM(fee), 0)")
                .eq("status", PayStatus.PAID)
                .eq("biz_type", PayBizType.GRATUITY))));
        vo.setRefundAmount(sum(payOrderMapper.selectObjs(new QueryWrapper<PayOrder>()
                .select("COALESCE(SUM(refund_amount), 0)")
                .eq("status", PayStatus.REFUNDED))));
        vo.setTodayPaidOrders(payOrderMapper.selectCount(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getStatus, PayStatus.PAID)
                .ge(PayOrder::getPaidTime, startOfDay)));

        // 找回
        long found = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.COMPLETED)
                .isNotNull(Post::getFoundTime)
                .in(Post::getType, PostType.LOST, PostType.FOUND));
        long ended = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .in(Post::getStatus, PostStatus.COMPLETED, PostStatus.EXPIRED)
                .in(Post::getType, PostType.LOST, PostType.FOUND));
        vo.setFoundPosts(found);
        vo.setFoundRate(ended == 0 ? 0D : Math.round(found * 10000D / ended) / 100D);

        // 风险
        vo.setOverdueAuditPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.PENDING_AUDIT)
                .lt(Post::getCreateTime, now.minusHours(4))));
        vo.setSplitWaitReview(splitPlanMapper.selectCount(new LambdaQueryWrapper<SplitPlan>()
                .eq(SplitPlan::getStatus, "WAIT_REVIEW")));
        vo.setExpiredFeeders(feederProfileMapper.selectCount(new LambdaQueryWrapper<FeederProfile>()
                .in(FeederProfile::getStatus, "EXPIRED", "PAUSED")
                .lt(FeederProfile::getCertExpireTime, now)));
        return vo;
    }

    private long sum(List<Object> values) {
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0L;
        }
        Object value = values.get(0);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
