package com.jaycode.rongpet.module.experience;

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
