from pathlib import Path
b=Path('src/main/java/com/jaycode/rongpet/module/experience')
(b/'PetAvatarService.java').write_text('''package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.pet.service.PetService;
import com.jaycode.rongpet.module.pet.mapper.PetMapper;
import com.jaycode.rongpet.module.pet.entity.Pet;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.experience.entity.PetAvatarTask;
import com.jaycode.rongpet.module.experience.mapper.*;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PetAvatarService {
    public static final int MAX_PAYMENT_REBUILDS = 1;
    private final PetAvatarTaskMapper tasks;
    private final ExperienceLockMapper locks;
    private final PetService pets;
    private final PetMapper petMapper;
    private final SysConfigService config;
    private final PayService payments;
    private final PayOrderMapper orders;
    @Value("${rongpet.ai.gateway-url:}") private String gatewayUrl;

    public AvatarQuota quota(long user, long pet) {
        pets.requireOwned(user, pet);
        long used = used(pet);
        return new AvatarQuota(used, Math.max(0, config.getInt("pet.avatar.free.count", 3) - used),
                config.getLong("pet.avatar.price.cents", 0), StringUtils.hasText(gatewayUrl));
    }

    @Transactional
    public AvatarTask create(long user, long petId, String style, String key) {
        pets.requireOwned(user, petId);
        Pet pet = locks.lockPet(petId);
        if (pet == null || !Objects.equals(pet.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "宠物不存在");
        PetAvatarTask old = tasks.selectOne(new LambdaQueryWrapper<PetAvatarTask>()
                .eq(PetAvatarTask::getUserId, user).eq(PetAvatarTask::getRequestKey, key));
        if (old != null) {
            if (!Objects.equals(old.getPetId(), petId)) throw new BizException(CommonErrorCode.CONFLICT, "幂等键已被其他宠物任务使用");
            return view(old);
        }
        if (!StringUtils.hasText(pet.getAvatarUrl())) throw new BizException(CommonErrorCode.BAD_REQUEST, "请先上传宠物真实头像再生成形象");
        if (!StringUtils.hasText(gatewayUrl)) throw new BizException(CommonErrorCode.CONFLICT, "AI图像服务尚未配置，暂不扣次数或收费");
        if (!Set.of("STORYBOOK", "SOFT3D", "WATERCOLOR").contains(style)) throw new BizException(CommonErrorCode.BAD_REQUEST, "画风不支持");
        if (tasks.selectCount(new LambdaQueryWrapper<PetAvatarTask>().eq(PetAvatarTask::getPetId, petId)
                .in(PetAvatarTask::getStatus, "WAIT_PAY", "QUEUED", "RUNNING")) > 0)
            throw new BizException(CommonErrorCode.CONFLICT, "该宠物已有待完成的生成任务");
        boolean free = used(petId) < config.getInt("pet.avatar.free.count", 3);
        long price = free ? 0 : config.getLong("pet.avatar.price.cents", 0);
        if (!free && price <= 0) throw new BizException(CommonErrorCode.CONFLICT, "付费生成价格尚未配置，暂不收费");
        PetAvatarTask task = new PetAvatarTask();
        task.setUserId(user);
        task.setPetId(petId);
        task.setRequestKey(key);
        task.setStyle(style);
        task.setSourceUrl(pet.getAvatarUrl().trim());
        task.setFreeUse(free ? 1 : 0);
        task.setPrice(price);
        task.setStatus(free ? "QUEUED" : "WAIT_PAY");
        task.setPayRebuildCount(0);
        tasks.insert(task);
        return view(task);
    }

    private long used(long pet) {
        return tasks.selectCount(new LambdaQueryWrapper<PetAvatarTask>().eq(PetAvatarTask::getPetId, pet)
                .eq(PetAvatarTask::getFreeUse, 1).eq(PetAvatarTask::getStatus, "SUCCEEDED"));
    }

    public AvatarTask task(long user, long id) {
        return view(require(user, id));
    }

    public List<AvatarTask> list(long user, long pet) {
        pets.requireOwned(user, pet);
        return tasks.selectList(new LambdaQueryWrapper<PetAvatarTask>().eq(PetAvatarTask::getPetId, pet)
                .eq(PetAvatarTask::getUserId, user).orderByDesc(PetAvatarTask::getCreateTime).orderByDesc(PetAvatarTask::getId).last("LIMIT 30"))
                .stream().map(this::view).toList();
    }

    @Transactional
    public PayInitVO pay(long user, long id) {
        PetAvatarTask task = tasks.lock(id);
        if (task == null || !Objects.equals(task.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "生成任务不存在");
        if (!"WAIT_PAY".equals(task.getStatus())) throw new BizException(CommonErrorCode.CONFLICT, "任务无需付款");
        PayOrder order = task.getOrderNo() == null ? null : orders.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOutTradeNo, task.getOrderNo()));
        boolean create = task.getOrderNo() == null;
        if (!create) {
            if (order == null) throw new BizException(CommonErrorCode.CONFLICT, "支付单不存在，请联系客服");
            if ("CLOSED".equals(order.getStatus())) {
                int rebuilds = task.getPayRebuildCount() == null ? 0 : task.getPayRebuildCount();
                if (rebuilds >= MAX_PAYMENT_REBUILDS) throw new BizException(CommonErrorCode.CONFLICT, "该任务支付单已重建一次，请联系客服处理");
                task.setPayRebuildCount(rebuilds + 1);
                create = true;
            } else if (!"CREATED".equals(order.getStatus())) {
                throw new BizException(CommonErrorCode.CONFLICT, "订单已处理，请刷新任务状态");
            }
        }
        if (create) {
            order = payments.createOrder(user, "AI_AVATAR", String.valueOf(id), task.getPrice(), 0, task.getPrice());
            task.setOrderNo(order.getOutTradeNo());
            changed(tasks.updateById(task));
        }
        return payments.initiate(order);
    }

    @Transactional
    public void select(long user, long pet, long id) {
        Pet target = pets.requireOwned(user, pet);
        PetAvatarTask task = require(user, id);
        if (!Objects.equals(task.getPetId(), pet) || !"SUCCEEDED".equals(task.getStatus())) throw new BizException(CommonErrorCode.BAD_REQUEST, "只能选择此宠物已成功生成的形象");
        target.setAiAvatarUrl(task.getResultUrl());
        changed(petMapper.updateById(target));
    }

    private PetAvatarTask require(long user, long id) {
        PetAvatarTask task = tasks.selectById(id);
        if (task == null || !Objects.equals(task.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "生成任务不存在");
        return task;
    }

    private AvatarTask view(PetAvatarTask task) {
        return new AvatarTask(task.getId(), task.getPetId(), task.getStyle(), task.getFreeUse(), task.getPrice(), task.getStatus(), task.getResultUrl(), task.getErrorMessage(), task.getCreateTime());
    }

    private void changed(int rows) {
        if (rows != 1) throw new BizException(CommonErrorCode.CONFLICT, "任务已更新，请刷新后重试");
    }
}
''',encoding='utf-8')
(b/'PetAvatarWorker.java').write_text('''package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jaycode.rongpet.module.experience.entity.PetAvatarTask;
import com.jaycode.rongpet.module.experience.mapper.PetAvatarTaskMapper;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PetAvatarWorker {
    private final PetAvatarTaskMapper tasks;
    private final ObjectMapper json;
    private final PayService payments;
    @Value("${rongpet.ai.gateway-url:}") private String url;
    @Value("${rongpet.ai.gateway-key:}") private String key;

    @Scheduled(fixedDelay = 15000)
    @SchedulerLock(name = "job.petAvatar", lockAtMostFor = "PT30M", lockAtLeastFor = "PT15S")
    public void process() {
        tasks.update(null, transition("RUNNING", "FAILED")
                .set(PetAvatarTask::getErrorMessage, "生成超时，请重试")
                .lt(PetAvatarTask::getUpdateTime, LocalDateTime.now().minusMinutes(10)));
        if (StringUtils.hasText(url)) {
            for (PetAvatarTask task : tasks.selectList(new LambdaQueryWrapper<PetAvatarTask>()
                    .eq(PetAvatarTask::getStatus, "QUEUED").orderByAsc(PetAvatarTask::getCreateTime).last("LIMIT 2"))) {
                if (tasks.update(null, transition("QUEUED", "RUNNING").eq(PetAvatarTask::getId, task.getId())) == 0) continue;
                generate(task);
            }
        }
        // 生成网关临时下线也继续处理历史失败退款；任务锁覆盖整个退款循环。
        for (PetAvatarTask task : tasks.selectList(new LambdaQueryWrapper<PetAvatarTask>()
                .eq(PetAvatarTask::getStatus, "FAILED").eq(PetAvatarTask::getFreeUse, 0)
                .isNotNull(PetAvatarTask::getOrderNo).last("LIMIT 20"))) {
            try {
                payments.refundByOrderNo(task.getOrderNo(), "AI形象生成失败原路退款", "SYSTEM");
            } catch (Exception error) {
                log.warn("AI生成退款待重试 taskId={}", task.getId(), error);
            }
        }
    }

    private void generate(PetAvatarTask task) {
        try {
            if (!StringUtils.hasText(task.getSourceUrl())) throw new IllegalStateException("任务缺少真实源图");
            String body = json.writeValueAsString(Map.of("taskId", String.valueOf(task.getId()), "sourceUrl", task.getSourceUrl(), "style", task.getStyle(), "preserveFeatures", true));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("图像网关返回失败");
            var result = json.readTree(response.body());
            String image = result.path("imageUrl").asText();
            if (!image.startsWith("https://") || !result.path("safetyPassed").asBoolean(false)) throw new IllegalStateException("结果未通过安全检查");
            tasks.update(null, transition("RUNNING", "SUCCEEDED").eq(PetAvatarTask::getId, task.getId()).set(PetAvatarTask::getResultUrl, image));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            tasks.update(null, transition("RUNNING", "FAILED").eq(PetAvatarTask::getId, task.getId())
                    .set(PetAvatarTask::getErrorMessage, "生成失败，免费次数未消耗，付费款项进入退款处理"));
            log.warn("AI生成任务失败 taskId={}", task.getId(), error);
        }
    }

    private LambdaUpdateWrapper<PetAvatarTask> transition(String from, String to) {
        // 后台任务没有登录会话，显式写入时间并递增版本；WHERE状态作为原子抢占条件。
        return new LambdaUpdateWrapper<PetAvatarTask>().eq(PetAvatarTask::getStatus, from)
                .set(PetAvatarTask::getStatus, to).set(PetAvatarTask::getUpdateTime, LocalDateTime.now()).setSql("version=version+1");
    }
}
''',encoding='utf-8')
(b/'RewardObjectionReminder.java').write_text('''package com.jaycode.rongpet.module.experience;

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
''',encoding='utf-8')
p=Path('src/main/java/com/jaycode/rongpet/module/post/entity/Post.java');s=p.read_text(encoding='utf-8').replace('private LocalDateTime rewardExpireTime;', 'private LocalDateTime rewardExpireTime;\n\n    private LocalDateTime refundNoticeTime;');p.write_text(s,encoding='utf-8')
