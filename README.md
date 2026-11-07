# rong-pet（蓉宠帮）后端

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

### Flyway 迁移版本

| 版本 | 内容 |
|---|---|
| V1 | 账号、管理员、登录日志、系统配置 |
| V2 | 宠物档案、帖子、线索、认领及操作日志 |
| V3 | 喂养员档案与认证、喂养请求 |
| V4 | 支付单、资金流水、分账方案/明细、感谢金、置顶记录 |
| V5 | 运营后台扩展（举报、审计、风控专题等） |
| V6 | 线索 48h 提醒字段与索引、喂养纠纷表、支付单退款重试计数、配置 29/30（过期保留期、敏感词库） |

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

## 当前进度（V1.3 功能全量）

- [x] M0 基础设施：统一响应/错误码/全局异常/traceId、Sa-Token 双账号、MyBatis-Plus 审计填充与分页、AES-GCM + HMAC、OpenAPI 三组文档
- [x] M1 账号：微信登录（真实/mock）、我的资料、实名补充、后台账号密码登录（BCrypt、5 次失败锁 30 分钟、登录日志）
- [x] M2 宠物档案与帖子：发布/编辑/我的/详情/下架/延长/确认找回/疑似匹配/审核/到期/草稿清理/操作日志
- [x] M2 线索与认领：提交/有效标记/作弊校验/评分；认领先审后发、拾宠人确认/拒绝、确认归还
- [x] M3 支付与资金：悬赏/置顶/感谢金下单、微信 V3 JSAPI + mock 通道、幂等入账、原路退款、到期退款与提醒、支付超时关单
- [x] M3 分账：评分权重计算、后台核验、按收款人汇总转付、公示口径与明细可见性
- [x] M4 喂养：喂养员认证/资料三步/列表详情/暂停封禁恢复、喂养请求信息模式、48 小时超时关闭
- [x] M5 运营后台：用户与风控专题、数据看板、帖子/置顶/认领/喂养/支付/分账/感谢金/举报/配置/审计日志
- [x] M5 定时任务：ShedLock 分布式锁 + 12 个调度任务（见下）
- [x] 全量编译与测试通过（`./mvnw test`）
- [ ] 微信登录/支付回调运行时 spike（待小程序 AppID 与商户号凭证）
- [x] 图片上传：COS 私有桶预签名 + 本地联调兜底，单张 ≤5MB、jpg/png
- [x] 敏感词/导流拦截（FR-4.3）：词库可配置，帖子命中进人工复核队列，线索/认领命中直接拦截
- [x] 合规注销（NFR-4）：在途资金/订单/喂养/帖子未结清时拒绝，注销后脱敏并禁止登录
- [x] 喂养纠纷凭证（FR-7.5）：双方提交凭证、后台查看并归档留痕
- [x] 运营报表（11.3）：日报/周报/月报 + CSV 导出；资金流水对账导出
- [x] 退款异常队列（FR-5.2.10）：失败计数、自动重试、连续 3 次转人工
- [x] 过期帖保留展示 30 天（可配置），之后仅本人可见；线索 48h 未处理提醒
- [ ] 图片内容安全检测（微信 msgSecCheck）待凭证就绪后接入（当前由敏感词服务兜底）
- [ ] 接入真实 MySQL/Redis 后的上下文启动验证

## 接口清单

### 小程序端 `/app/v1/**`

| 模块 | 接口 |
|---|---|
| 认证 | `POST /auth/login`、`POST /auth/logout` |
| 用户 | `GET/PUT /user/me`、`POST /user/real-name`、`POST /user/cancel`（合规注销，在途业务未结清拒绝） |
| 公开 | `GET /public/posts`、`/public/posts/found`、`/public/posts/{id}`、`/public/feeders`、`/public/feeders/{id}`、`/public/announcement`、`/public/agreements/{type}`（无需登录） |
| 宠物 | `GET/POST /pets`、`GET/PUT/DELETE /pets/{id}` |
| 上传 | `POST /files/upload` |
| 帖子 | `POST /posts`、`PUT /posts/{id}`、`GET /posts/mine`、`GET /posts/{id}`、`POST /posts/{id}/take-down`、`/extend`、`/found`、`GET /posts/match` |
| 线索 | `POST/GET /posts/{id}/clues`、`POST /posts/{id}/clues/{clueId}/valid|invalid`、`POST /posts/{id}/scores`、`POST /posts/clues/{clueId}/public` |
| 认领 | `POST/GET /posts/{id}/claims`、`GET /claims/mine`、`POST /claims/{id}/confirm|refuse`、`POST /posts/{id}/confirm-return` |
| 支付 | `POST /pay/reward/{postId}`、`GET /pay/orders` |
| 置顶 | `POST /posts/{id}/top/free`、`POST /posts/{id}/top/pay` |
| 感谢金 | `POST/GET /posts/{id}/gratuity` |
| 喂养员 | `POST /feeders/apply`、`GET /feeders/apply/mine`、`GET /feeders/profile/mine`、`PUT /feeders/profile`、`POST /feeders/profile/pause|resume` |
| 喂养请求 | `POST /feeding`、`GET /feeding/mine`、`GET /feeding/feeder`、`GET /feeding/{id}`、`POST /feeding/{id}/contact|complete|close` |
| 喂养纠纷 | `POST /feeding/disputes`（双方提交凭证）、`GET /feeding/{id}/disputes`（双方查看处理记录） |
| 消息 | `GET /messages`、`GET /messages/unread-count`、`POST /messages/{id}/read`、`POST /messages/read-all` |
| 举报 | `POST /reports` |

### 运营后台 `/admin/v1/**`

| 模块 | 接口 |
|---|---|
| 认证 | `POST /auth/login`、`POST /auth/logout`、`GET /auth/me` |
| 看板 | `GET /dashboard/overview` |
| 用户 | `GET /users`、`GET /users/{id}`、`POST /users/{id}/action`、`GET /users/risks` |
| 帖子 | `GET /posts`、`GET /posts/{id}`、`GET /posts/{id}/logs`、`POST /posts/{id}/approve|reject|take-down` |
| 置顶 | `GET /tops`、`POST /tops/free/{id}/approve|reject`、`POST /tops/manual` |
| 认领 | `GET /claims`、`POST /claims/{id}/audit` |
| 喂养员 | `GET /feeders/applications`、`POST /feeders/applications/{id}/audit`、`GET /feeders/profiles`、`POST /feeders/profiles/{id}/action` |
| 喂养请求 | `GET /feeding`、`GET /feeding/{id}`、`POST /feeding/{id}/close`、`GET /feeding/disputes`、`POST /feeding/disputes/{id}/archive` |
| 支付 | `GET /pays`、`GET /pays/{id}`、`POST /pays/{id}/refund`（SUPER）、`GET /pays/refund-failures`、`POST /pays/{id}/retry-refund`（SUPER） |
| 分账 | `GET /splits`、`GET /splits/{id}/items`、`POST /splits/{id}/approve|transfer`（转付 SUPER） |
| 感谢金 | `GET /gratuity`、`POST /gratuity/{id}/transfer|refund`（SUPER） |
| 举报 | `GET /reports`、`POST /reports/{id}/handle` |
| 运营报表 | `GET /stats-reports`、`GET /stats-reports/export`（日报/周报/月报，CSV 导出，导出写审计） |
| 资金流水 | `GET /fund-flows`、`GET /fund-flows/export`（对账 CSV 导出，SUPER，单次上限 10000 条） |
| 配置 | `GET /configs`、`PUT /configs`（SUPER） |
| 审计 | `GET /audit-logs`（SUPER） |

### 回调

- `POST /callback/wxpay/order`：微信支付 V3 通知，验签后幂等入账。

## 定时任务

| 任务 | 频率 | 说明 |
|---|---|---|
| 悬赏到期退款 | 每日 00:10 | 期满未找回，本金与服务费原路退回 |
| 普通帖到期下架 | 每日 00:10 | 展示中普通帖到期转 EXPIRED |
| 草稿清理 | 每日 00:15 | 删除 7 天前草稿 |
| 喂养员认证过期 | 每日 00:20 | 证书过期转 EXPIRED |
| 评分逾期默认分账 | 每小时 | 超过 `split.score.timeout.days`（默认 7 天）按均分 |
| 喂养请求超时关闭 | 每小时 | 48 小时未完成自动关闭 |
| 置顶到期失效 | 每 10 分钟 | 置顶位失效并释放 |
| 支付超时关单 | 每 10 分钟 | 30 分钟未支付转 CLOSED |
| 喂养员认证到期提醒 | 每日 09:00 | 提前 30 天站内提醒 |
| 悬赏到期提醒 | 每日 09:30 | 到期前 24 小时提醒 |
| 线索未处理提醒 | 每小时 :20 | 线索 48h 未处理，按帖聚合仅提醒一次（`job.remindCluePending`） |
| 退款失败自动重试 | 每小时 15/45 分 | 重试未达 3 次的失败退款，满 3 次转人工（`job.retryFailedRefund`） |

任务统一由 `module/job/ScheduledJobs` 编排，并通过 ShedLock + Redis 保证多实例下只执行一次。

## 本地支付联调

dev 默认开启 mock 支付（`rongpet.pay.mock=${WX_PAY_MOCK:true}`），下单即视为支付成功，可完整联调悬赏托管、置顶、感谢金与退款流程。
接入真实微信支付时设置 `rongpet.pay.mock=false` 并配置 `rongpet.wx.pay.*` 商户参数。



## V1.4 本次确认更新

新版需求见 [V1.4确认需求](doc/V1.4确认需求.md)，新增接口、AI网关协议、验证结果与尚待接入项见 [V1.4开发与接入说明](doc/V1.4开发与接入说明.md)。本次新增 Flyway V7。上文 V1.3 进度勾选不代表 V1.4 或生产环境已验收。

HTML原型：`prototype/index.html`，可直接打开；更新扩展源码后运行 `python tools/build_prototype.py` 内联。
