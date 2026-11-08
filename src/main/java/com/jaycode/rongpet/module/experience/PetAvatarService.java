package com.jaycode.rongpet.module.experience;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.pet.service.PetService;
import com.jaycode.rongpet.module.pet.mapper.PetMapper;
import com.jaycode.rongpet.module.pet.entity.Pet;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.experience.entity.PetAvatarTask;
import com.jaycode.rongpet.module.experience.mapper.*;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;

/**
 * AI 宠物形象生成的下单与配额服务（实际调网关在 PetAvatarWorker）。
 * <ul>
 *   <li>配额：每只宠物有免费成功次数（配置 pet.avatar.free.count），仅 SUCCEEDED 的免费任务计数；超额后按配置价收费</li>
 *   <li>幂等：requestKey 与用户唯一，重复提交直接返回既有任务；同一宠物同时只允许一个未完成任务</li>
 *   <li>付费：任务先落 WAIT_PAY，支付单关闭后允许重建一次（{@link #MAX_PAYMENT_REBUILDS}），支付成功由回调推进队列</li>
 *   <li>选图：成功结果由用户手动设为宠物 aiAvatarUrl，与真实头像分离</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PetAvatarService {
    /** 支付单被关闭后允许重建的最大次数，超过则提示联系客服 */
    public static final int MAX_PAYMENT_REBUILDS = 1;
    private final PetAvatarTaskMapper tasks;
    private final ExperienceLockMapper locks;
    private final PetService pets;
    private final PetMapper petMapper;
    private final SysConfigService config;
    private final PayService payments;
    private final PayOrderMapper orders;
    @Value("${rongpet.ai.gateway-url:}") private String gatewayUrl;

    public AvatarQuota quota(long user, long pet) {
        pets.requireOwned(user, pet);
        long used = used(pet);
        return new AvatarQuota(used, Math.max(0, config.getInt("pet.avatar.free.count", 3) - used),
                config.getLong("pet.avatar.price.cents", 0), StringUtils.hasText(gatewayUrl));
    }

    @Transactional
    public AvatarTask create(long user, long petId, String style, String key) {
        pets.requireOwned(user, petId);
        Pet pet = locks.lockPet(petId);
        if (pet == null || !Objects.equals(pet.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "宠物不存在");
        PetAvatarTask old = tasks.selectOne(new LambdaQueryWrapper<PetAvatarTask>()
                .eq(PetAvatarTask::getUserId, user).eq(PetAvatarTask::getRequestKey, key));
        if (old != null) {
            if (!Objects.equals(old.getPetId(), petId)) throw new BizException(CommonErrorCode.CONFLICT, "幂等键已被其他宠物任务使用");
            return view(old);
        }
        if (!StringUtils.hasText(pet.getAvatarUrl())) throw new BizException(CommonErrorCode.BAD_REQUEST, "请先上传宠物真实头像再生成形象");
        if (!StringUtils.hasText(gatewayUrl)) throw new BizException(CommonErrorCode.CONFLICT, "AI图像服务尚未配置，暂不扣次数或收费");
        if (!Set.of("STORYBOOK", "SOFT3D", "WATERCOLOR").contains(style)) throw new BizException(CommonErrorCode.BAD_REQUEST, "画风不支持");
        if (tasks.selectCount(new LambdaQueryWrapper<PetAvatarTask>().eq(PetAvatarTask::getPetId, petId)
                .in(PetAvatarTask::getStatus, "WAIT_PAY", "QUEUED", "RUNNING")) > 0)
            throw new BizException(CommonErrorCode.CONFLICT, "该宠物已有待完成的生成任务");
        boolean free = used(petId) < config.getInt("pet.avatar.free.count", 3);
        long price = free ? 0 : config.getLong("pet.avatar.price.cents", 0);
        if (!free && price <= 0) throw new BizException(CommonErrorCode.CONFLICT, "付费生成价格尚未配置，暂不收费");
        PetAvatarTask task = new PetAvatarTask();
        task.setUserId(user);
        task.setPetId(petId);
        task.setRequestKey(key);
        task.setStyle(style);
        task.setSourceUrl(pet.getAvatarUrl().trim());
        task.setFreeUse(free ? 1 : 0);
        task.setPrice(price);
        task.setStatus(free ? "QUEUED" : "WAIT_PAY");
        task.setPayRebuildCount(0);
        try {
            tasks.insert(task);
        } catch (DuplicateKeyException error) {
            // 并发下相同 requestKey 命中 uk_avatar_request，重查返回既有任务，保证幂等而不是抛500。
            PetAvatarTask existing = tasks.selectOne(new LambdaQueryWrapper<PetAvatarTask>()
                    .eq(PetAvatarTask::getUserId, user).eq(PetAvatarTask::getRequestKey, key));
            if (existing != null) return view(existing);
            throw error;
        }
        return view(task);
    }

    private long used(long pet) {
        return tasks.selectCount(new LambdaQueryWrapper<PetAvatarTask>().eq(PetAvatarTask::getPetId, pet)
                .eq(PetAvatarTask::getFreeUse, 1).eq(PetAvatarTask::getStatus, "SUCCEEDED"));
    }

    public AvatarTask task(long user, long id) {
        return view(require(user, id));
    }

    public List<AvatarTask> list(long user, long pet) {
        pets.requireOwned(user, pet);
        return tasks.selectList(new LambdaQueryWrapper<PetAvatarTask>().eq(PetAvatarTask::getPetId, pet)
                .eq(PetAvatarTask::getUserId, user).orderByDesc(PetAvatarTask::getCreateTime).orderByDesc(PetAvatarTask::getId).last("LIMIT 30"))
                .stream().map(this::view).toList();
    }

    @Transactional
    public PayInitVO pay(long user, long id) {
        PetAvatarTask task = tasks.lock(id);
        if (task == null || !Objects.equals(task.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "生成任务不存在");
        if (!"WAIT_PAY".equals(task.getStatus())) throw new BizException(CommonErrorCode.CONFLICT, "任务无需付款");
        PayOrder order = task.getOrderNo() == null ? null : orders.selectOne(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOutTradeNo, task.getOrderNo()));
        boolean create = task.getOrderNo() == null;
        if (!create) {
            if (order == null) throw new BizException(CommonErrorCode.CONFLICT, "支付单不存在，请联系客服");
            if ("CLOSED".equals(order.getStatus())) {
                int rebuilds = task.getPayRebuildCount() == null ? 0 : task.getPayRebuildCount();
                if (rebuilds >= MAX_PAYMENT_REBUILDS) throw new BizException(CommonErrorCode.CONFLICT, "该任务支付单已重建一次，请联系客服处理");
                task.setPayRebuildCount(rebuilds + 1);
                create = true;
            } else if (!"CREATED".equals(order.getStatus())) {
                throw new BizException(CommonErrorCode.CONFLICT, "订单已处理，请刷新任务状态");
            }
        }
        if (create) {
            order = payments.createOrder(user, "AI_AVATAR", String.valueOf(id), task.getPrice(), 0, task.getPrice());
            task.setOrderNo(order.getOutTradeNo());
            changed(tasks.updateById(task));
        }
        return payments.initiate(order);
    }

    @Transactional
    public void select(long user, long pet, long id) {
        Pet target = pets.requireOwned(user, pet);
        PetAvatarTask task = require(user, id);
        if (!Objects.equals(task.getPetId(), pet) || !"SUCCEEDED".equals(task.getStatus())) throw new BizException(CommonErrorCode.BAD_REQUEST, "只能选择此宠物已成功生成的形象");
        target.setAiAvatarUrl(task.getResultUrl());
        changed(petMapper.updateById(target));
    }

    private PetAvatarTask require(long user, long id) {
        PetAvatarTask task = tasks.selectById(id);
        if (task == null || !Objects.equals(task.getUserId(), user)) throw new BizException(CommonErrorCode.NOT_FOUND, "生成任务不存在");
        return task;
    }

    private AvatarTask view(PetAvatarTask task) {
        return new AvatarTask(task.getId(), task.getPetId(), task.getStyle(), task.getFreeUse(), task.getPrice(), task.getStatus(), task.getResultUrl(), task.getErrorMessage(), task.getCreateTime());
    }

    private void changed(int rows) {
        if (rows != 1) throw new BizException(CommonErrorCode.CONFLICT, "任务已更新，请刷新后重试");
    }
}
