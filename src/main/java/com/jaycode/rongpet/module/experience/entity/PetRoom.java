package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 爱宠小屋的固定展示场景，对应表 t_pet_room。
 * 一只宠物一条记录（uk_room_pet），由用户切换场景；未配置时前端默认 SUNNY。
 * 可选场景受系统配置 pet.room.scenes 控制。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pet_room")
public class PetRoom extends BaseEntity {

    /** 所属宠物 ID，唯一 */
    private Long petId;

    /** 宠物主人用户 ID */
    private Long userId;

    /** 场景：SUNNY 晴天 / GARDEN 花园 / NIGHT 夜晚 */
    private String scene;

}
