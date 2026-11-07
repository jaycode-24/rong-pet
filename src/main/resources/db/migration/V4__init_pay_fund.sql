-- =====================================================================
-- V4：支付单 / 资金流水 / 分账方案与明细 / 感谢金 / 置顶记录
-- 所有金额字段均为分（bigint）；资金类表只允许通过服务层状态机变更
-- =====================================================================

CREATE TABLE t_pay_order (
    id            BIGINT       NOT NULL COMMENT '支付单ID（雪花）',
    out_trade_no  VARCHAR(64)  NOT NULL COMMENT '平台商户单号，唯一',
    biz_type      VARCHAR(16)  NOT NULL COMMENT 'REWARD/TOP/GRATUITY',
    biz_no        VARCHAR(64)  NOT NULL COMMENT '业务单号（帖子/置顶/感谢金）',
    user_id       BIGINT       NOT NULL COMMENT '支付用户',
    amount        BIGINT       NOT NULL COMMENT '本金（分）',
    fee           BIGINT       NOT NULL DEFAULT 0 COMMENT '平台服务费（分）',
    total         BIGINT       NOT NULL COMMENT '实付合计（分）',
    status        VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/CLOSED/REFUNDING/REFUNDED/FAILED',
    channel       VARCHAR(16)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/WXPAY',
    transaction_id VARCHAR(64) NULL COMMENT '微信支付单号',
    paid_time     DATETIME     NULL,
    refund_no     VARCHAR(64)  NULL COMMENT '微信退款单号/平台退款单号',
    refund_amount BIGINT       NULL COMMENT '退款金额（分）',
    refund_time   DATETIME     NULL,
    fail_reason   VARCHAR(255) NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by     BIGINT       NULL,
    update_by     BIGINT       NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_out_trade_no (out_trade_no),
    KEY idx_pay_user (user_id, create_time),
    KEY idx_pay_biz (biz_type, biz_no),
    KEY idx_pay_status (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付单表';

CREATE TABLE t_fund_flow (
    id         BIGINT       NOT NULL COMMENT '流水ID（雪花）',
    flow_no    VARCHAR(32)  NOT NULL COMMENT '流水号，唯一',
    user_id    BIGINT       NULL COMMENT '关联用户，平台留存收入可空',
    biz_type   VARCHAR(16)  NOT NULL COMMENT 'REWARD/SPLIT/GRATUITY/TOP/REFUND',
    biz_no     VARCHAR(64)  NOT NULL COMMENT '业务单号（帖子/线索/感谢金/置顶单）',
    direction  VARCHAR(8)   NOT NULL COMMENT 'IN收入/OUT支出',
    amount     BIGINT       NOT NULL COMMENT '金额（分）',
    status     VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'PROCESSING/SUCCESS/FAILED',
    voucher_no VARCHAR(64)  NULL COMMENT '微信单号或转账凭证号',
    operator   VARCHAR(64)  NULL COMMENT '后台操作人',
    remark     VARCHAR(255) NULL,
    finish_time DATETIME    NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   BIGINT      NULL,
    update_by   BIGINT      NULL,
    is_deleted  TINYINT     NOT NULL DEFAULT 0,
    version     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_no (flow_no),
    KEY idx_flow_user (user_id, create_time),
    KEY idx_flow_biz (biz_type, biz_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='资金流水表';

CREATE TABLE t_split_plan (
    id              BIGINT      NOT NULL COMMENT '分账方案ID（雪花）',
    post_id         BIGINT      NOT NULL COMMENT '帖子ID',
    total_amount    BIGINT      NOT NULL COMMENT '参与分账本金（分）',
    status          VARCHAR(16) NOT NULL DEFAULT 'WAIT_REVIEW' COMMENT 'WAIT_REVIEW/APPROVED/PAID',
    review_required TINYINT     NOT NULL DEFAULT 0 COMMENT '是否达到人工复核阈值',
    approve_time    DATETIME    NULL COMMENT '核验通过时间',
    paid_time       DATETIME    NULL COMMENT '转付完成时间',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       BIGINT      NULL,
    update_by       BIGINT      NULL,
    is_deleted      TINYINT     NOT NULL DEFAULT 0,
    version         INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_split_post (post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='悬赏分账方案表';

CREATE TABLE t_split_item (
    id         BIGINT      NOT NULL COMMENT '分账明细ID（雪花）',
    plan_id    BIGINT      NOT NULL COMMENT '方案ID',
    post_id    BIGINT      NOT NULL COMMENT '帖子ID',
    clue_id    BIGINT      NOT NULL COMMENT '线索ID',
    user_id    BIGINT      NOT NULL COMMENT '线索提供者',
    score      TINYINT     NOT NULL COMMENT '评分快照1-5',
    amount     BIGINT      NOT NULL COMMENT '分得金额（分）',
    pay_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAID',
    paid_time  DATETIME    NULL,
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   BIGINT     NULL,
    update_by   BIGINT     NULL,
    is_deleted  TINYINT    NOT NULL DEFAULT 0,
    version     INT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_split_item_clue (plan_id, clue_id),
    KEY idx_split_item_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='悬赏分账明细表';

CREATE TABLE t_gratuity (
    id                 BIGINT       NOT NULL COMMENT '感谢金ID（雪花）',
    post_id            BIGINT       NOT NULL COMMENT '招领帖ID',
    payer_id           BIGINT       NOT NULL COMMENT '失主（支付人）',
    amount             BIGINT       NOT NULL COMMENT '感谢金金额（分）',
    fee                BIGINT       NOT NULL DEFAULT 0 COMMENT '平台抽成（分）',
    net_amount         BIGINT       NOT NULL COMMENT '拾宠人实收（分）',
    payee_account_enc  VARCHAR(255) NOT NULL COMMENT '拾宠人收款账户密文',
    payee_account_hmac VARCHAR(64)  NOT NULL COMMENT '收款账户HMAC',
    order_no           VARCHAR(64)  NULL COMMENT '支付单号',
    status             VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/FROZEN/PAID/REFUNDED',
    paid_time          DATETIME     NULL COMMENT '转付完成时间',
    refund_reason      VARCHAR(255) NULL,
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by          BIGINT       NULL,
    update_by          BIGINT       NULL,
    is_deleted         TINYINT      NOT NULL DEFAULT 0,
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_gratuity_post (post_id),
    KEY idx_gratuity_status (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='感谢金表';

CREATE TABLE t_top_record (
    id            BIGINT       NOT NULL COMMENT '置顶记录ID（雪花）',
    post_id       BIGINT       NOT NULL COMMENT '帖子ID',
    type          VARCHAR(16)  NOT NULL COMMENT 'PAID/FREE/MANUAL',
    days          INT          NOT NULL COMMENT '置顶天数',
    start_time    DATETIME     NULL COMMENT '生效时间',
    end_time      DATETIME     NULL COMMENT '到期时间',
    order_no      VARCHAR(64)  NULL COMMENT '付费置顶支付单号',
    screenshot_url VARCHAR(512) NULL COMMENT '免费置顶截图',
    reason        VARCHAR(255) NULL COMMENT '人工置顶理由/驳回原因',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REJECTED/EXPIRED',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by     BIGINT       NULL,
    update_by     BIGINT       NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_top_post (post_id),
    KEY idx_top_status (status, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='置顶记录表';
