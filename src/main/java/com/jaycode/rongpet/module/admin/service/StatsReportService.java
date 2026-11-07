package com.jaycode.rongpet.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.admin.dto.StatsReportVO;
import com.jaycode.rongpet.module.admin.entity.AdminAuditLog;
import com.jaycode.rongpet.module.admin.mapper.AdminAuditLogMapper;
import com.jaycode.rongpet.module.feeder.entity.FeederProfile;
import com.jaycode.rongpet.module.feeder.mapper.FeederProfileMapper;
import com.jaycode.rongpet.module.feeding.entity.FeedingRequest;
import com.jaycode.rongpet.module.feeding.mapper.FeedingRequestMapper;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 运营报表服务（PRD 11.3）：日报/周报/月报，导出字段与页面展示完全一致。
 * 全部统计实时读取业务表，金额单位为分；确认收入仅统计支付成功订单，退款按退款单冲减。
 */
@Service
@RequiredArgsConstructor
public class StatsReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final FeedingRequestMapper feedingMapper;
    private final FeederProfileMapper feederProfileMapper;
    private final PayOrderMapper payOrderMapper;
    private final SplitPlanMapper splitPlanMapper;
    private final ReportMapper reportMapper;
    private final AdminAuditLogMapper auditLogMapper;

    public StatsReportVO report(String periodType, LocalDate date) {
        LocalDate anchor = date == null ? LocalDate.now() : date;
        LocalDate start;
        LocalDate end;
        String type = periodType == null ? "DAY" : periodType.trim().toUpperCase();
        switch (type) {
            case "WEEK" -> {
                // 周一至周日
                start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = start.plusDays(6);
            }
            case "MONTH" -> {
                start = anchor.withDayOfMonth(1);
                end = anchor.with(TemporalAdjusters.lastDayOfMonth());
            }
            default -> {
                type = "DAY";
                start = anchor;
                end = anchor;
            }
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();

        StatsReportVO vo = new StatsReportVO();
        vo.setPeriodType(type);
        vo.setStartDate(start);
        vo.setEndDate(end);
        vo.setGeneratedAt(LocalDateTime.now().format(TS));

        fillDaily(vo, from, to);
        if (!"DAY".equals(type)) {
            fillWeekly(vo, from, to);
        }
        if ("MONTH".equals(type)) {
            fillMonthly(vo, from, to);
        }
        vo.setRows(buildRows(vo));
        return vo;
    }

    private void fillDaily(StatsReportVO vo, LocalDateTime from, LocalDateTime to) {
        vo.setNewUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, from).lt(User::getCreateTime, to)));
        vo.setNewPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .ge(Post::getCreateTime, from).lt(Post::getCreateTime, to)));
        vo.setNewFeedingRequests(feedingMapper.selectCount(new LambdaQueryWrapper<FeedingRequest>()
                .ge(FeedingRequest::getCreateTime, from).lt(FeedingRequest::getCreateTime, to)));

        long rewardFee = sumFee(from, to, PayBizType.REWARD);
        long topFee = sumAmount(from, to, PayBizType.TOP);
        long gratuityFee = sumFee(from, to, PayBizType.GRATUITY);
        vo.setRewardFeeIncome(rewardFee);
        vo.setTopIncome(topFee);
        vo.setGratuityFeeIncome(gratuityFee);
        vo.setIncomeCents(rewardFee + topFee + gratuityFee);

        vo.setRefundCents(sumRefund(from, to));

        long reports = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .ge(Report::getCreateTime, from).lt(Report::getCreateTime, to));
        long overdueAudit = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.PENDING_AUDIT)
                .lt(Post::getCreateTime, LocalDateTime.now().minusHours(4)));
        long refundFailed = payOrderMapper.selectCount(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getStatus, PayStatus.FAILED)
                .ge(PayOrder::getUpdateTime, from).lt(PayOrder::getUpdateTime, to));
        vo.setExceptionCount(reports + overdueAudit + refundFailed);
        vo.setReportedCount(reports);
    }

    private void fillWeekly(StatsReportVO vo, LocalDateTime from, LocalDateTime to) {
        vo.setNewCertifiedFeeders(feederProfileMapper.selectCount(new LambdaQueryWrapper<FeederProfile>()
                .ge(FeederProfile::getCreateTime, from).lt(FeederProfile::getCreateTime, to)));
        vo.setRewardOrders(payOrderMapper.selectCount(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getBizType, PayBizType.REWARD)
                .ge(PayOrder::getCreateTime, from).lt(PayOrder::getCreateTime, to)));
        vo.setRewardPaidOrders(payOrderMapper.selectCount(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getBizType, PayBizType.REWARD)
                .eq(PayOrder::getStatus, PayStatus.PAID)
                .ge(PayOrder::getPaidTime, from).lt(PayOrder::getPaidTime, to)));
        List<SplitPlan> paidPlans = splitPlanMapper.selectList(new LambdaQueryWrapper<SplitPlan>()
                .eq(SplitPlan::getStatus, "PAID")
                .ge(SplitPlan::getPaidTime, from).lt(SplitPlan::getPaidTime, to));
        vo.setSplitPaidCount(paidPlans.size());
        vo.setSplitPaidAmount(paidPlans.stream().mapToLong(p -> p.getTotalAmount() == null ? 0 : p.getTotalAmount()).sum());
    }

    private void fillMonthly(StatsReportVO vo, LocalDateTime from, LocalDateTime to) {
        long found = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.COMPLETED)
                .isNotNull(Post::getFoundTime)
                .ge(Post::getFoundTime, from).lt(Post::getFoundTime, to)
                .in(Post::getType, PostType.LOST, PostType.FOUND));
        long ended = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .in(Post::getStatus, PostStatus.COMPLETED, PostStatus.EXPIRED)
                .ge(Post::getUpdateTime, from).lt(Post::getUpdateTime, to)
                .in(Post::getType, PostType.LOST, PostType.FOUND));
        vo.setFoundPosts(found);
        vo.setEndedPosts(ended);
        vo.setFoundRate(ended == 0 ? 0D : Math.round(found * 10000D / ended) / 100D);
        vo.setPunishedUsers(auditLogMapper.selectCount(new LambdaQueryWrapper<AdminAuditLog>()
                .in(AdminAuditLog::getAction, "USER_LIMIT_POST", "USER_LIMIT_TRADE", "USER_BAN", "USER_FROZEN")
                .ge(AdminAuditLog::getCreateTime, from).lt(AdminAuditLog::getCreateTime, to)));
    }

    private long sumFee(LocalDateTime from, LocalDateTime to, String bizType) {
        return sum(payOrderMapper.selectObjs(new QueryWrapper<PayOrder>()
                .select("COALESCE(SUM(fee), 0)")
                .eq("status", PayStatus.PAID)
                .eq("biz_type", bizType)
                .ge("paid_time", from).lt("paid_time", to)));
    }

    private long sumAmount(LocalDateTime from, LocalDateTime to, String bizType) {
        // 置顶费为本金口径（fee 为 0），按实付合计统计
        return sum(payOrderMapper.selectObjs(new QueryWrapper<PayOrder>()
                .select("COALESCE(SUM(total), 0)")
                .eq("status", PayStatus.PAID)
                .eq("biz_type", bizType)
                .ge("paid_time", from).lt("paid_time", to)));
    }

    private long sumRefund(LocalDateTime from, LocalDateTime to) {
        return sum(payOrderMapper.selectObjs(new QueryWrapper<PayOrder>()
                .select("COALESCE(SUM(refund_amount), 0)")
                .eq("status", PayStatus.REFUNDED)
                .ge("refund_time", from).lt("refund_time", to)));
    }

    private long sum(List<Object> values) {
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0L;
        }
        Object value = values.get(0);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private List<StatsReportVO.Row> buildRows(StatsReportVO vo) {
        List<StatsReportVO.Row> rows = new ArrayList<>();
        rows.add(new StatsReportVO.Row("新增用户", String.valueOf(vo.getNewUsers()), "人", ""));
        rows.add(new StatsReportVO.Row("新增帖子", String.valueOf(vo.getNewPosts()), "条", ""));
        rows.add(new StatsReportVO.Row("新增喂养请求", String.valueOf(vo.getNewFeedingRequests()), "条", ""));
        rows.add(new StatsReportVO.Row("确认收入", yuan(vo.getIncomeCents()), "元", "悬赏服务费+置顶费+感谢金抽成"));
        rows.add(new StatsReportVO.Row("退款金额", yuan(vo.getRefundCents()), "元", "原路退款冲减"));
        rows.add(new StatsReportVO.Row("异常数", String.valueOf(vo.getExceptionCount()), "个", "举报+审核超时+退款失败"));
        if (!"DAY".equals(vo.getPeriodType())) {
            rows.add(new StatsReportVO.Row("新增认证喂养员", String.valueOf(vo.getNewCertifiedFeeders()), "人", "供给变化"));
            rows.add(new StatsReportVO.Row("悬赏下单/支付成功", vo.getRewardOrders() + "/" + vo.getRewardPaidOrders(), "单", "转化漏斗"));
            rows.add(new StatsReportVO.Row("分账转付", vo.getSplitPaidCount() + " 笔 / " + yuan(vo.getSplitPaidAmount()), "笔/元", "按收款人汇总"));
        }
        if ("MONTH".equals(vo.getPeriodType())) {
            rows.add(new StatsReportVO.Row("收入-悬赏服务费", yuan(vo.getRewardFeeIncome()), "元", "收入结构"));
            rows.add(new StatsReportVO.Row("收入-置顶费", yuan(vo.getTopIncome()), "元", "收入结构"));
            rows.add(new StatsReportVO.Row("收入-感谢金抽成", yuan(vo.getGratuityFeeIncome()), "元", "收入结构"));
            rows.add(new StatsReportVO.Row("找回率", vo.getFoundRate() + "%", "", vo.getFoundPosts() + "/" + vo.getEndedPosts() + " 条"));
            rows.add(new StatsReportVO.Row("被举报内容", String.valueOf(vo.getReportedCount()), "条", "内容质量"));
            rows.add(new StatsReportVO.Row("处置账号", String.valueOf(vo.getPunishedUsers()), "个", "内容质量"));
        }
        return rows;
    }

    /** 分转元，两位小数，与 PRD 金额展示规范一致 */
    private String yuan(long cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
