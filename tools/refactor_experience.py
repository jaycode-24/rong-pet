from pathlib import Path
r=Path('.'); b=r/'src/main/java/com/jaycode/rongpet/module/experience'
models={
'UserFavorite':('t_user_favorite','Long userId;Long postId'),
'Feedback':('t_feedback','Long userId;String category;String content;String status'),
'FeedbackReply':('t_feedback_reply','Long feedbackId;Long adminId;String content'),
'PetRoom':('t_pet_room','Long petId;Long userId;String scene'),
'RewardDispute':('t_reward_dispute','Long postId;Long userId;String content;String evidence;String status;String decision;Long adminId'),
'Conversation':('t_conversation','Long postId;Long ownerId;Long guestId'),
'ConversationMessage':('t_conversation_message','Long conversationId;Long senderId;String content'),
'PetAvatarTask':('t_pet_avatar_task','Long userId;Long petId;String requestKey;String style;String sourceUrl;Integer freeUse;Long price;String status;String resultUrl;String errorMessage;String orderNo;Integer payRebuildCount')}
for d in ['entity','mapper','dto']:(b/d).mkdir(exist_ok=True)
for name,(table,fields) in models.items():
 (b/'entity'/f'{name}.java').write_text(f'''package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("{table}")
public class {name} extends BaseEntity {{
'''+''.join('    private '+f.strip()+';\n\n' for f in fields.split(';'))+'}\n',encoding='utf-8')
 extra=''
 if name=='PetAvatarTask':extra='''    @Select("SELECT * FROM t_pet_avatar_task WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    PetAvatarTask lock(@Param("id") long id);
'''
 if name=='Feedback':extra='''    @Select("SELECT * FROM t_feedback WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Feedback lock(@Param("id") long id);
'''
 if name=='Conversation':extra='''    @Select("SELECT * FROM t_conversation WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Conversation lock(@Param("id") long id);
'''
 if name=='UserFavorite':extra='''    @Insert("INSERT INTO t_user_favorite(id,user_id,post_id,create_by,update_by) VALUES (#{id},#{user},#{post},#{user},#{user}) ON DUPLICATE KEY UPDATE is_deleted=0,update_time=NOW(),update_by=#{user},version=version+1")
    int restoreOrInsert(@Param("id") long id, @Param("user") long user, @Param("post") long post);
'''
 if name=='PetRoom':extra='''    @Insert("INSERT INTO t_pet_room(id,pet_id,user_id,scene,create_by,update_by) VALUES (#{id},#{pet},#{user},#{scene},#{user},#{user}) ON DUPLICATE KEY UPDATE scene=VALUES(scene),is_deleted=0,update_time=NOW(),update_by=#{user},version=version+1")
    int saveScene(@Param("id") long id,@Param("pet") long pet,@Param("user") long user,@Param("scene") String scene);
'''
 (b/'mapper'/f'{name}Mapper.java').write_text(f'''package com.jaycode.rongpet.module.experience.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jaycode.rongpet.module.experience.entity.{name};
import org.apache.ibatis.annotations.*;

public interface {name}Mapper extends BaseMapper<{name}> {{
{extra}}}
''',encoding='utf-8')
(b/'mapper'/'ExperienceLockMapper.java').write_text('''package com.jaycode.rongpet.module.experience.mapper;

import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.pet.entity.Pet;
import org.apache.ibatis.annotations.*;

/** 跨模块共享锁顺序：先帖子／宠物，再锁其业务对象。 */
public interface ExperienceLockMapper {
    @Select("SELECT * FROM t_post WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Post lockPost(@Param("id") long id);

    @Select("SELECT * FROM t_pet WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Pet lockPet(@Param("id") long id);

    @Update("UPDATE t_post SET refund_notice_time=NOW(),update_time=NOW(),version=version+1 WHERE id=#{id} AND refund_notice_time IS NULL AND is_deleted=0")
    int claimReminder(@Param("id") long id);
}
''',encoding='utf-8')
# 新增迁移，不改V7及以前版本。
sql=['-- V8 标准审计字段、乐观锁、逻辑删除及AI付款重建上限。']
for name,(table,_) in models.items():
 if name in ['UserFavorite','PetRoom']:
  sql.append(f'ALTER TABLE {table} DROP PRIMARY KEY, ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;')
  sql.append(f'ALTER TABLE {table} ADD UNIQUE KEY uk_{"favorite_pair(user_id,post_id)" if name=="UserFavorite" else "room_pet(pet_id)"};')
 if name=='PetRoom':sql.append(f'ALTER TABLE {table} ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;')
 if name in ['UserFavorite','PetRoom','FeedbackReply','Conversation','ConversationMessage']:
  sql.append(f'ALTER TABLE {table} ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;')
 sql.append(f'ALTER TABLE {table} ADD COLUMN create_by BIGINT NULL, ADD COLUMN update_by BIGINT NULL, ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0, ADD COLUMN version INT NOT NULL DEFAULT 0;')
sql+=['ALTER TABLE t_pet_avatar_task ADD COLUMN pay_rebuild_count INT NOT NULL DEFAULT 0;',"ALTER TABLE t_reward_dispute ADD COLUMN pending_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' AND is_deleted=0 THEN 1 ELSE NULL END) STORED, ADD UNIQUE KEY uk_dispute_pending(post_id,user_id,pending_guard);"]
(r/'src/main/resources/db/migration/V8__experience_standards.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
