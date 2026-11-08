package com.jaycode.rongpet.module.experience.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jaycode.rongpet.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 宠物形象生成任务，对应表 t_pet_avatar_task。
 * 免费任务直接排队，付费任务先 WAIT_PAY，支付回调后才入队；由 PetAvatarWorker 轮询队列调外部图像网关。
 * 状态流转：WAIT_PAY（待支付）→ QUEUED（排队中）→ RUNNING（生成中）→ SUCCEEDED（成功）/ FAILED（失败、超时）。
 * 同一用户 requestKey 唯一（uk_avatar_request），用作下单幂等键；失败的付费单由定时任务扫描后原路退款。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pet_avatar_task")
public class PetAvatarTask extends BaseEntity {

    /** 发起用户 ID */
    private Long userId;

    /** 生成形象所属宠物 ID */
    private Long petId;

    /** 客户端幂等键，与 userId 组成唯一键，重复提交返回既有任务而非新建 */
    private String requestKey;

    /** 画风：STORYBOOK 绘本 / SOFT3D 柔和3D / WATERCOLOR 水彩 */
    private String style;

    /** 源图地址，取宠物真实头像；AI 形象与真实头像分离存储 */
    private String sourceUrl;

    /** 是否占用免费额度：1 免费 / 0 付费；仅成功的免费任务计入已用次数 */
    private Integer freeUse;

    /** 本次生成价格（分），免费任务为 0 */
    private Long price;

    /** WAIT_PAY/QUEUED/RUNNING/SUCCEEDED/FAILED */
    private String status;

    /** 生成成功后的结果图片地址（私有对象存储 https 地址） */
    private String resultUrl;

    /** 失败或超时原因，用于前端提示，如“生成超时，请重试” */
    private String errorMessage;

    /** 关联支付单 out_trade_no，免费任务为空；失败单据此原路退款 */
    private String orderNo;

    /** 支付单重建次数，支付单关闭后只允许重建 1 次，超限转人工 */
    private Integer payRebuildCount;

}
