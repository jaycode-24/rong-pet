package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.clue.entity.Clue;
import com.jaycode.rongpet.module.clue.mapper.ClueMapper;
import com.jaycode.rongpet.module.experience.mapper.ExperienceLockMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RewardObjectionReminder {
    private final PostMapper posts;
    private final ClueMapper clues;
    private final ExperienceLockMapper locks;
    private final MessageService messages;
    private final SysConfigService config;

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "job.rewardObjectionReminder", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void remind() {
        for (Post post : posts.selectList(new LambdaQueryWrapper<Post>().eq(Post::getRewardStatus, "FROZEN")
                .eq(Post::getStatus, "REWARD_FROZEN").le(Post::getRewardExpireTime, LocalDateTime.now())
                .isNull(Post::getRefundNoticeTime).last("LIMIT 100"))) {
            if (locks.claimReminder(post.getId()) == 0) continue;
            String text = "悬赏已到期，进入" + config.getInt("reward.refund.objection.hours", 72)
                    + "小时异议窗口；若已协助找回，请及时提交证据。有争议或有效线索待核验时暂停退款。";
            messages.send(post.getUserId(), MessageService.TYPE_FUND, "悬赏退款异议窗口", text, "POST", String.valueOf(post.getId()));
            clues.selectList(new LambdaQueryWrapper<Clue>().eq(Clue::getPostId, post.getId())).stream()
                    .map(Clue::getUserId).distinct().forEach(user -> messages.send(user, MessageService.TYPE_FUND,
                            "悬赏到期，请核对找回情况", text, "POST", String.valueOf(post.getId())));
        }
    }
}
