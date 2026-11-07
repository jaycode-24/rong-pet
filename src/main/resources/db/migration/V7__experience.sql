-- V1.4 体验能力，既有迁移保持不变。
CREATE TABLE t_user_favorite(user_id BIGINT NOT NULL,post_id BIGINT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(user_id,post_id),KEY idx_favorite_user_time(user_id,create_time));
CREATE TABLE t_feedback(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,category VARCHAR(32) NOT NULL,content TEXT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,KEY idx_feedback_user(user_id,create_time),KEY idx_feedback_status(status,create_time));
CREATE TABLE t_feedback_reply(id BIGINT PRIMARY KEY,feedback_id BIGINT NOT NULL,admin_id BIGINT NOT NULL,content TEXT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY idx_feedback_reply(feedback_id,create_time));
CREATE TABLE t_pet_room(pet_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,scene VARCHAR(16) NOT NULL DEFAULT 'SUNNY');
CREATE TABLE t_reward_dispute(id BIGINT PRIMARY KEY,post_id BIGINT NOT NULL,user_id BIGINT NOT NULL,content TEXT NOT NULL,evidence TEXT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',decision TEXT NULL,admin_id BIGINT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,KEY idx_dispute_post(post_id,status));

CREATE TABLE t_conversation(id BIGINT PRIMARY KEY,post_id BIGINT NOT NULL,owner_id BIGINT NOT NULL,guest_id BIGINT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_conversation(post_id,guest_id),KEY idx_conversation_owner(owner_id),KEY idx_conversation_guest(guest_id));
CREATE TABLE t_conversation_message(id BIGINT PRIMARY KEY,conversation_id BIGINT NOT NULL,sender_id BIGINT NOT NULL,content TEXT NOT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY idx_conversation_message(conversation_id,id),KEY idx_sender_time(sender_id,create_time));

ALTER TABLE t_pet ADD COLUMN ai_avatar_url VARCHAR(1024) NULL COMMENT 'AI形象，与真实头像分离';
CREATE TABLE t_pet_avatar_task(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,pet_id BIGINT NOT NULL,request_key VARCHAR(64) NOT NULL,style VARCHAR(32) NOT NULL,source_url VARCHAR(1024) NOT NULL,free_use TINYINT NOT NULL,price BIGINT NOT NULL DEFAULT 0,status VARCHAR(20) NOT NULL,result_url VARCHAR(1024) NULL,error_message VARCHAR(255) NULL,order_no VARCHAR(64) NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_avatar_request(user_id,request_key),KEY idx_avatar_pet(pet_id,status),KEY idx_avatar_status(status,create_time));

INSERT INTO t_sys_config(id,config_key,config_value,remark) VALUES
(31,'pet.avatar.free.count','3','每宠物免费成功生成次数'),
(32,'pet.avatar.price.cents','0','每次生成收费，分；0表示未定价，禁止付费下单'),
(33,'reward.refund.objection.hours','72','悬赏到期退款异议窗口小时数'),
(34,'help.faq','[{"question":"如何发布寻宠？","answer":"首页点击我丢了宠物。"}]','帮助中心FAQ JSON'),
(35,'pet.room.scenes','SUNNY,GARDEN,NIGHT','爱宠固定场景列表');

ALTER TABLE t_post ADD COLUMN refund_notice_time DATETIME NULL;
