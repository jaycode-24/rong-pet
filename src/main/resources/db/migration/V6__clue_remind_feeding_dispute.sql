-- =====================================================================
-- V6：线索 48h 未处理提醒 + 喂养纠纷凭证 + 过期帖保留期配置
-- =====================================================================

-- 线索 48 小时未处理提醒时间，仅提醒一次（PRD FR-5.2.6）
ALTER TABLE t_clue
    ADD COLUMN remind_time DATETIME NULL COMMENT '48小时未处理提醒时间' AFTER detail_public;
CREATE INDEX idx_clue_remind ON t_clue (status, remind_time, create_time);

-- 退款失败重试计数（PRD FR-5.2.10：连续3次失败转人工线下处理）
ALTER TABLE t_pay_order
    ADD COLUMN refund_retry_count INT NOT NULL DEFAULT 0 COMMENT '退款失败重试次数' AFTER fail_reason;

-- 喂养纠纷凭证与处理记录（PRD FR-7.5，信息模式无资金，纠纷以留痕为主）
CREATE TABLE t_feeding_dispute (
    id              BIGINT       NOT NULL COMMENT '纠纷ID（雪花）',
    feeding_id      BIGINT       NOT NULL COMMENT '喂养请求ID',
    submitter_id    BIGINT       NOT NULL COMMENT '提交人（用户或喂养员）',
    side            VARCHAR(16)  NOT NULL COMMENT 'USER用户/FEEDER喂养员',
    reason          VARCHAR(64)  NOT NULL COMMENT '纠纷原因标签',
    description     VARCHAR(500) NOT NULL COMMENT '情况描述',
    evidence_images VARCHAR(2048) NULL COMMENT '凭证图片JSON数组，0-9张',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/ARCHIVED',
    handle_remark   VARCHAR(500) NULL COMMENT '管理员处理说明',
    handle_admin_id BIGINT       NULL COMMENT '处理管理员',
    handle_time     DATETIME     NULL COMMENT '处理时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       BIGINT       NULL,
    update_by       BIGINT       NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dispute_feeding (feeding_id),
    KEY idx_dispute_status (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='喂养纠纷凭证记录表';

-- 过期帖公开保留天数（PRD FR-5.2.10：保留展示30天后仅本人可见）
INSERT INTO t_sys_config (id, config_key, config_value, remark) VALUES
(29, 'post.expired.retain.days', '30', '已过期帖子公开保留天数，之后仅本人可见'),
(30, 'content.sensitive.words', '活体买卖,活体交易,有偿领养,领养费,出售幼犬,出售幼猫,包配种,宠物医疗,兽药,特效药,代办证,办证,刻章,贷款,刷单,兼职刷单,套现,加微信,加v,扫码进群,二维码,赌博,博彩,色情,上门服务', '敏感词库（逗号分隔），命中内容进入人工复核或拦截');
