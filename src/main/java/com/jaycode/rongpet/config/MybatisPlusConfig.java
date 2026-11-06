package com.jaycode.rongpet.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.jaycode.rongpet.security.StpAdminUtil;
import com.jaycode.rongpet.security.StpUserUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：分页、乐观锁插件与审计字段自动填充。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
                strictInsertFill(metaObject, "version", Integer.class, 0);
                Long operatorId = currentOperatorId();
                if (operatorId != null) {
                    strictInsertFill(metaObject, "createBy", Long.class, operatorId);
                    strictInsertFill(metaObject, "updateBy", Long.class, operatorId);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                Long operatorId = currentOperatorId();
                if (operatorId != null) {
                    strictUpdateFill(metaObject, "updateBy", Long.class, operatorId);
                }
            }

            /** 小程序用户与后台管理员两套会话，取当前实际登录者，均未登录则返回 null */
            private Long currentOperatorId() {
                Long userId = StpUserUtil.getLoginIdOrNull();
                return userId != null ? userId : StpAdminUtil.getLoginIdOrNull();
            }
        };
    }
}
