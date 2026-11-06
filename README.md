# rong-pet（蓉宠）后端

成都宠物本地信任平台后端服务，同时承载：

- **微信小程序端 API**：`/app/v1/**`（普通用户 + 喂养员轻量视图）
- **PC 运营后台 API**：`/admin/v1/**`（管理员，独立鉴权）
- **微信回调入口**：`/callback/**`（支付、内容安全等，验签 + IP 白名单）

业务范围与规则以 `doc/成都宠物本地信任平台PRD-V1.3.docx` 为准；技术设计见评审计划文档。

## 技术栈

| 分类 | 选型 |
|---|---|
| 框架 | Spring Boot 3.5.3 / Java 21（模块化单体） |
| 持久层 | MyBatis-Plus 3.5.12 + Flyway + MySQL 8 |
| 缓存/会话 | Redis 7 + Sa-Token 1.46（用户/管理员双账号隔离） |
| 微信能力 | WxJava 4.8（小程序、微信支付） |
| 对象存储 | 腾讯云 COS（私有 bucket + 预签名 URL） |
| 定时任务 | Spring Scheduling + ShedLock（多实例防重复） |
| 接口文档 | springdoc-openapi（`/swagger-ui.html`，app/admin/callback 三组） |
| 工具 | MapStruct、Hutool、Lombok |

## 目录结构

```
src/main/java/com/jaycode/rongpet
├── common        统一响应 R、错误码、异常、基类实体、traceId 过滤器
├── config        MyBatis-Plus、OpenAPI、加解密等配置
├── security      Sa-Token 双账号工具、AES-GCM/HMAC 加解密组件
├── wx            微信小程序/支付属性装配（无凭证时不创建 Bean）
├── cos           腾讯云 COS 装配
└── module        业务模块（M1 起按 account/pet/post/clue/pay 等划分）
src/main/resources
├── application.yaml / application-dev.yaml / application-prod.yaml
└── db/migration  Flyway 版本化建表脚本（V1 起）
```

## 本地启动

前置：JDK 21、Docker（用于起 MySQL/Redis）。

```bash
# 1. 启动依赖（首次会拉取镜像）
docker compose up -d

# 2. 启动应用（dev profile，Flyway 自动建表）
./mvnw spring-boot:run

# 3. 验证
#    健康检查：http://localhost:8080/actuator/health
#    接口文档：http://localhost:8080/swagger-ui.html
```

无 Docker 时，也可使用本机 MySQL 8 / Redis，修改 `application-dev.yaml` 中的连接信息。

## 关键约定

1. **金额**：前后端交互一律以"分"为单位（Long），数据库 `bigint`，禁止浮点；
2. **敏感字段**：手机号/身份证/地址/收款账户采用 AES-GCM 密文列 + HMAC-SHA256 索引列，展示层脱敏；
3. **密钥**：生产环境通过 `RONGPET_CRYPTO_AES_KEY` / `RONGPET_CRYPTO_HMAC_KEY` 环境变量注入，参见 `.env.example`；
4. **数据库变更**：只允许新增 Flyway 脚本，禁止手改库结构、禁止修改已执行的脚本；
5. **接口幂等**：支付/退款回调、确认找回、评分、转付登记必须幂等；
6. **权限隔离**：后台接口与小程序接口路径、鉴权完全分离，服务端按登录身份兜底过滤数据。

## 当前进度（M0 里程碑）

- [x] Spring Boot 降级 3.5.3，全量依赖锁定并通过编译
- [x] 统一响应/错误码/全局异常/traceId 基础设施
- [x] Sa-Token 双账号体系、MyBatis-Plus 审计填充与分页
- [x] AES-GCM + HMAC 加解密组件及单元测试
- [x] WxJava / COS 条件装配、OpenAPI 三组文档
- [x] Flyway V1：账号模块三表
- [ ] 微信登录/支付回调运行时 spike（待小程序 AppID 与商户号凭证）
- [ ] 接入真实 MySQL/Redis 后的上下文启动验证
