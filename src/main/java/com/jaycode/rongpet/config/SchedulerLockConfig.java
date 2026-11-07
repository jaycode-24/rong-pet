package com.jaycode.rongpet.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 定时任务分布式锁配置。
 * 单机部署时锁同样生效（防止同一定时任务被重复触发），多实例部署时保证同一任务同一时刻只执行一次。
 * 默认锁最长持有 10 分钟，任务实际耗时更长时需在 @SchedulerLock 上单独设置。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "rongpet");
    }
}
