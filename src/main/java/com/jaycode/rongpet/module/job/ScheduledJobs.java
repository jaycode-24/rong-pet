package com.jaycode.rongpet.module.job;

import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.clue.service.ClueService;
import com.jaycode.rongpet.module.feeder.service.FeederService;
import com.jaycode.rongpet.module.feeding.service.FeedingService;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.service.PostService;
import com.jaycode.rongpet.module.post.service.TopService;
import com.jaycode.rongpet.module.split.service.SplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全部定时任务集中管理。每个任务均加 ShedLock 分布式锁，多实例部署时同一任务只执行一次。
 * 任务内部自行处理单个业务异常，避免一条坏数据阻断整批处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private static final int SCORE_BATCH = 100;

    private final PayService payService;
    private final PostService postService;
    private final TopService topService;
    private final SplitService splitService;
    private final FeederService feederService;
    private final FeedingService feedingService;
    private final ClueService clueService;
    private final SysConfigService sysConfigService;

    /** 悬赏到期退款：每日 00:10 */
    @Scheduled(cron = "0 10 0 * * ?")
    @SchedulerLock(name = "job.expireRewardPosts", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireRewardPosts() {
        run("悬赏到期退款", () -> {
            int count = payService.expireRewardPosts();
            if (count > 0) {
                log.info("悬赏到期退款完成，共 {} 笔", count);
            }
        });
    }

    /** 普通展示帖到期下架：每日 00:10 */
    @Scheduled(cron = "0 10 0 * * ?")
    @SchedulerLock(name = "job.expireShowingPosts", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireShowingPosts() {
        run("普通帖到期下架", () -> {
            int count = postService.expireShowingPosts();
            if (count > 0) {
                log.info("普通帖到期下架完成，共 {} 条", count);
            }
        });
    }

    /** 草稿清理（保留 7 天）：每日 00:15 */
    @Scheduled(cron = "0 15 0 * * ?")
    @SchedulerLock(name = "job.cleanExpiredDrafts", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanExpiredDrafts() {
        run("草稿清理", () -> {
            int count = postService.cleanExpiredDrafts();
            if (count > 0) {
                log.info("草稿清理完成，共 {} 条", count);
            }
        });
    }

    /** 喂养员认证过期：每日 00:20 */
    @Scheduled(cron = "0 20 0 * * ?")
    @SchedulerLock(name = "job.expireCertifications", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireCertifications() {
        run("喂养员认证过期", () -> {
            int count = feederService.expireCertifications();
            if (count > 0) {
                log.info("喂养员认证过期处理完成，共 {} 人", count);
            }
        });
    }

    /** 评分逾期按默认规则均分：每小时第 5 分钟 */
    @Scheduled(cron = "0 5 * * * ?")
    @SchedulerLock(name = "job.autoDefaultScore", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void autoDefaultScore() {
        run("评分逾期默认分账", () -> {
            int days = (int) sysConfigService.getLong("split.score.timeout.days", 7L);
            LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, days));
            List<Post> posts = splitService.overdueScoreCandidates(cutoff, SCORE_BATCH);
            for (Post post : posts) {
                try {
                    splitService.autoDefault(post);
                } catch (Exception e) {
                    log.error("评分逾期默认分账失败 postId={}", post.getId(), e);
                }
            }
            if (!posts.isEmpty()) {
                log.info("评分逾期默认分账处理 {} 条", posts.size());
            }
        });
    }

    /** 喂养请求 48 小时超时关闭：每小时第 10 分钟 */
    @Scheduled(cron = "0 10 * * * ?")
    @SchedulerLock(name = "job.feedingTimeoutClose", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void feedingTimeoutClose() {
        run("喂养请求超时关闭", () -> {
            int count = feedingService.timeoutClose();
            if (count > 0) {
                log.info("喂养请求超时关闭完成，共 {} 条", count);
            }
        });
    }

    /** 置顶到期失效：每 10 分钟 */
    @Scheduled(cron = "0 */10 * * * ?")
    @SchedulerLock(name = "job.expireTops", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void expireTops() {
        run("置顶到期失效", () -> {
            int count = topService.expireTops();
            if (count > 0) {
                log.info("置顶到期失效处理完成，共 {} 条", count);
            }
        });
    }

    /** 支付超时关单（30 分钟未支付）：每 10 分钟 */
    @Scheduled(cron = "0 3/10 * * * ?")
    @SchedulerLock(name = "job.closeTimeoutOrders", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void closeTimeoutOrders() {
        run("支付超时关单", () -> {
            int count = payService.closeTimeoutOrders();
            if (count > 0) {
                log.info("支付超时关单完成，共 {} 笔", count);
            }
        });
    }

    /** 退款失败自动重试（未达 3 次）：每 30 分钟 */
    @Scheduled(cron = "0 15,45 * * * ?")
    @SchedulerLock(name = "job.retryFailedRefund", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void retryFailedRefund() {
        run("退款失败自动重试", () -> {
            int count = payService.retryFailedRefunds();
            if (count > 0) {
                log.info("退款失败自动重试完成，共 {} 笔", count);
            }
        });
    }

    /** 喂养员认证到期提醒（提前 30 天）：每日 09:00 */
    @Scheduled(cron = "0 0 9 * * ?")
    @SchedulerLock(name = "job.remindCertExpiring", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void remindCertExpiring() {
        run("喂养员认证到期提醒", () -> {
            int count = feederService.remindCertExpiring();
            if (count > 0) {
                log.info("喂养员认证到期提醒发送 {} 条", count);
            }
        });
    }

    /** 悬赏到期前 24 小时提醒：每日 09:30 */
    @Scheduled(cron = "0 30 9 * * ?")
    @SchedulerLock(name = "job.remindRewardExpiring", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void remindRewardExpiring() {
        run("悬赏到期提醒", () -> {
            int count = payService.remindRewardExpiring();
            if (count > 0) {
                log.info("悬赏到期提醒发送 {} 条", count);
            }
        });
    }

    /** 线索 48 小时未处理提醒：每小时第 20 分钟 */
    @Scheduled(cron = "0 20 * * * ?")
    @SchedulerLock(name = "job.remindCluePending", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void remindCluePending() {
        run("线索未处理提醒", () -> {
            int count = clueService.remindPending();
            if (count > 0) {
                log.info("线索48小时未处理提醒完成，共 {} 条", count);
            }
        });
    }

    private void run(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("定时任务执行失败：{}", name, e);
        }
    }
}
