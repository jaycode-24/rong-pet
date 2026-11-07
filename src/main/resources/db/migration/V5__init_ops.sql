-- =====================================================================
-- V5：运营配置 / 站内消息 / 举报 / 后台审计日志 + 默认配置种子
-- =====================================================================

CREATE TABLE t_sys_config (
    id           BIGINT       NOT NULL COMMENT '配置ID（雪花）',
    config_key   VARCHAR(64)  NOT NULL COMMENT '配置键',
    config_value VARCHAR(2000) NOT NULL COMMENT '配置值',
    remark       VARCHAR(255) NULL COMMENT '说明',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    BIGINT       NULL,
    update_by    BIGINT       NULL,
    is_deleted   TINYINT      NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='运营配置表';

CREATE TABLE t_message (
    id         BIGINT       NOT NULL COMMENT '消息ID（雪花）',
    user_id    BIGINT       NOT NULL COMMENT '接收用户',
    type       VARCHAR(16)  NOT NULL COMMENT 'SYSTEM/audit订单/资金四类：SYSTEM/AUDIT/ORDER/FUND',
    title      VARCHAR(64)  NOT NULL,
    content    VARCHAR(500) NOT NULL,
    biz_type   VARCHAR(32)  NULL COMMENT '关联业务类型 POST/CLUE/CLAIM/FEEDING/PAY',
    biz_no     VARCHAR(64)  NULL COMMENT '关联业务单号',
    is_read    TINYINT      NOT NULL DEFAULT 0,
    read_time  DATETIME     NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   BIGINT      NULL,
    update_by   BIGINT      NULL,
    is_deleted  TINYINT     NOT NULL DEFAULT 0,
    version     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_msg_user (user_id, is_read, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站内消息表';

CREATE TABLE t_report (
    id            BIGINT       NOT NULL COMMENT '举报ID（雪花）',
    reporter_id   BIGINT       NOT NULL COMMENT '举报人',
    target_type   VARCHAR(16)  NOT NULL COMMENT 'POST/CLUE/USER/FEEDING',
    target_id     BIGINT       NOT NULL COMMENT '目标ID',
    reason        VARCHAR(64)  NOT NULL COMMENT '举报原因标签',
    description   VARCHAR(500) NULL COMMENT '补充说明',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED/INVALID',
    handle_remark VARCHAR(255) NULL COMMENT '处理说明',
    handle_admin_id BIGINT     NULL COMMENT '处理人',
    handle_time   DATETIME     NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by     BIGINT       NULL,
    update_by     BIGINT       NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_report_status (status, create_time),
    KEY idx_report_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='举报表';

CREATE TABLE t_admin_audit_log (
    id          BIGINT       NOT NULL COMMENT '审计日志ID（雪花）',
    admin_id    BIGINT       NOT NULL COMMENT '操作管理员',
    username    VARCHAR(64)  NOT NULL COMMENT '操作账号快照',
    action      VARCHAR(64)  NOT NULL COMMENT '操作类型',
    target_type VARCHAR(32)  NULL COMMENT '目标类型',
    target_id   BIGINT       NULL COMMENT '目标ID',
    detail      VARCHAR(1000) NULL COMMENT '变更详情JSON',
    ip          VARCHAR(64)  NULL COMMENT '操作IP',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_audit_time (create_time),
    KEY idx_admin_audit_admin (admin_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台操作审计日志（append-only，保留3年）';

-- 默认运营配置（后台可改，即时生效；存量业务按原值固化在单据上）
INSERT INTO t_sys_config (id, config_key, config_value, remark) VALUES
(1, 'reward.service.rate', '0.08', '悬赏服务费率 8%'),
(2, 'reward.service.cap.cents', '8000', '悬赏服务费封顶 80 元'),
(3, 'reward.min.cents', '10000', '悬赏金额下限 100 元'),
(4, 'reward.max.cents', '1000000', '悬赏金额系统上限 10000 元'),
(5, 'reward.large.review.cents', '500000', '大额实名补充阈值 5000 元'),
(6, 'reward.score.hours', '168', '评分时限 7 天'),
(7, 'split.review.cents', '300000', '分账人工复核阈值 3000 元'),
(8, 'clue.valid.max', '5', '单帖有效线索上限'),
(9, 'clue.per.user.max', '3', '单帖单人线索提交上限'),
(10, 'split.share.max.percent', '60', '单条线索分账占比上限'),
(11, 'gratuity.rate', '0.10', '感谢金平台抽成 10%'),
(12, 'gratuity.free.until', '', '感谢金免抽成截止日 yyyy-MM-dd，空为不免费'),
(13, 'gratuity.max.cents', '1000000', '感谢金上限 10000 元'),
(14, 'top.price.LOST.cents', '3000', '寻宠置顶 30 元'),
(15, 'top.price.FOUND.cents', '2000', '招领置顶 20 元'),
(16, 'top.price.BREED.cents', '4000', '相亲置顶 40 元'),
(17, 'top.days.LOST', '3', '寻宠置顶天数'),
(18, 'top.days.FOUND', '3', '招领置顶天数'),
(19, 'top.days.BREED', '7', '相亲置顶天数'),
(20, 'post.default.days', '7', '帖子默认有效期天数'),
(21, 'gate.factor', '8', '流量闸门系数'),
(22, 'announcement.text', '欢迎使用蓉宠：成都宠物互助与信任平台。', '首页公告'),
(23, 'claim.max.per.post', '20', '单帖认领申请上限'),
(24, 'top.free.max.per.month', '2', '每账号每月免费置顶申请上限'),
(25, 'agreement.REWARD', '悬赏托管规则与免责说明（V1.3）：平台仅提供信息展示与资金托管，悬赏为发布者自愿承诺的报酬，平台不对找回结果作出承诺。找回后按评分权重分配本金，平台留存不超过 min(本金×8%,80元) 服务费；未找回则本金与服务费全额原路退还。', '悬赏协议文本'),
(26, 'agreement.BREED', '配种风险提示（V1.3）：平台仅提供家养宠物信息发布，不参与配种交易，不对后代健康与品种纯度作任何承诺。近亲繁育会显著提高遗传病风险，请核实系谱并做好健康筛查。', '相亲协议文本'),
(27, 'agreement.FEEDING', '上门喂养服务须知（V1.3）：本板块为信息对接服务，平台仅对喂养员身份信息做形式核验，不经手任何费用与押金，不对服务质量作实质担保，交易与责任由双方自行承担。', '喂养协议文本'),
(28, 'agreement.PRIVACY', '隐私政策（V1.3）：平台遵循最小必要原则收集信息；手机号、身份证等敏感信息加密存储并脱敏展示；用户可查询、更正、注销个人信息。交易与资金流水按法定要求留存。', '隐私政策文本');
