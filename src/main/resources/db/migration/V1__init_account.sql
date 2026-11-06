-- =====================================================================
-- V1：账号模块初始化（小程序用户 / 后台管理员 / 管理员登录日志）
-- 约定：
--   1. 主键 bigint，应用层雪花 ID；金额一律 bigint 存分（本脚本暂不涉及）
--   2. 敏感字段：xxx_enc 存 AES-GCM 密文，xxx_hmac 存 HMAC-SHA256 索引用于查重
--   3. 每张业务表含统一审计字段；append-only 日志表除外
--   4. 城市字段预留，V1.0 仅开通成都
-- =====================================================================

CREATE TABLE t_user (
    id              BIGINT       NOT NULL COMMENT '用户ID（雪花）',
    open_id         VARCHAR(64)  NOT NULL COMMENT '微信 openid，账号主体',
    union_id        VARCHAR(64)  NULL COMMENT '微信 unionid，多端预留',
    nickname        VARCHAR(64)  NULL COMMENT '昵称',
    avatar_url      VARCHAR(512) NULL COMMENT '头像地址',
    phone_enc       VARCHAR(255) NULL COMMENT '手机号密文（AES-GCM）',
    phone_hmac      VARCHAR(64)  NULL COMMENT '手机号 HMAC 索引，查重与反作弊比对',
    status          VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/LIMIT_POST/LIMIT_TRADE/BANNED/FROZEN',
    real_name_enc   VARCHAR(255) NULL COMMENT '实名姓名密文（大额悬赏/喂养员认证）',
    id_card_enc     VARCHAR(255) NULL COMMENT '身份证号密文',
    id_card_hmac    VARCHAR(64)  NULL COMMENT '身份证号 HMAC 索引',
    city            VARCHAR(32)  NOT NULL DEFAULT 'CHENGDU' COMMENT '所属城市，预留多城市',
    register_time   DATETIME     NOT NULL COMMENT '注册时间',
    last_login_time DATETIME     NULL COMMENT '最近登录时间',
    cancel_time     DATETIME     NULL COMMENT '注销时间，注销即冻结',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by       BIGINT       NULL COMMENT '创建人',
    update_by       BIGINT       NULL COMMENT '更新人',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_id (open_id),
    KEY idx_phone_hmac (phone_hmac),
    KEY idx_status_create (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='小程序用户表';

CREATE TABLE t_admin_user (
    id              BIGINT       NOT NULL COMMENT '管理员ID（雪花）',
    username        VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 加盐哈希',
    real_name       VARCHAR(64)  NULL COMMENT '真实姓名',
    role            VARCHAR(32)  NOT NULL DEFAULT 'AUDITOR' COMMENT 'SUPER 超级管理员 / AUDITOR 审核员',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    lock_until      DATETIME     NULL COMMENT '登录锁定截止时间，连续5次错误锁30分钟',
    last_login_time DATETIME     NULL COMMENT '最近登录时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by       BIGINT       NULL COMMENT '创建人',
    update_by       BIGINT       NULL COMMENT '更新人',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台管理员表';

CREATE TABLE t_admin_login_log (
    id           BIGINT      NOT NULL COMMENT '日志ID（雪花）',
    admin_id     BIGINT      NULL COMMENT '管理员ID，账号不存在时为空',
    username     VARCHAR(64) NOT NULL COMMENT '登录账号',
    login_ip     VARCHAR(64) NOT NULL COMMENT '登录 IP',
    user_agent   VARCHAR(255) NULL COMMENT '客户端 UA',
    login_result TINYINT     NOT NULL COMMENT '结果：1成功 0失败',
    fail_reason  VARCHAR(255) NULL COMMENT '失败原因',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_admin_time (admin_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='管理员登录日志（append-only）';
