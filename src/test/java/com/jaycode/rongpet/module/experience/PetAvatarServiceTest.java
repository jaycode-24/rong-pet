package com.jaycode.rongpet.module.experience;

import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.config.service.SysConfigService;
import com.jaycode.rongpet.module.experience.dto.ExperienceViews.AvatarQuota;
import com.jaycode.rongpet.module.experience.entity.PetAvatarTask;
import com.jaycode.rongpet.module.experience.mapper.ExperienceLockMapper;
import com.jaycode.rongpet.module.experience.mapper.PetAvatarTaskMapper;
import com.jaycode.rongpet.module.pay.dto.PayInitVO;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.pay.service.PayService;
import com.jaycode.rongpet.module.pet.entity.Pet;
import com.jaycode.rongpet.module.pet.mapper.PetMapper;
import com.jaycode.rongpet.module.pet.service.PetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetAvatarServiceTest {

    @Mock private PetAvatarTaskMapper tasks;
    @Mock private ExperienceLockMapper locks;
    @Mock private PetService pets;
    @Mock private PetMapper petMapper;
    @Mock private SysConfigService config;
    @Mock private PayService payments;
    @Mock private PayOrderMapper orders;
    @InjectMocks private PetAvatarService service;

    private static final long USER = 1L;
    private static final long PET = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "gatewayUrl", "https://ai.example.com");
    }

    private Pet ownedPet(String avatarUrl) {
        Pet pet = new Pet();
        pet.setId(PET);
        pet.setUserId(USER);
        pet.setAvatarUrl(avatarUrl);
        return pet;
    }

    @Test
    @DisplayName("额度查询：已用1次，免费3次，剩余2次")
    void quota() {
        when(pets.requireOwned(USER, PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(tasks.selectCount(any())).thenReturn(1L);
        when(config.getInt("pet.avatar.free.count", 3)).thenReturn(3);
        when(config.getLong("pet.avatar.price.cents", 0)).thenReturn(0L);

        AvatarQuota quota = service.quota(USER, PET);

        assertThat(quota.used()).isEqualTo(1L);
        assertThat(quota.remaining()).isEqualTo(2L);
        assertThat(quota.providerConfigured()).isTrue();
    }

    @Test
    @DisplayName("未上传真实头像时拒绝创建任务")
    void rejectWithoutSourceImage() {
        when(pets.requireOwned(USER, PET)).thenReturn(ownedPet(null));
        when(locks.lockPet(PET)).thenReturn(ownedPet(null));
        when(tasks.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.create(USER, PET, "STORYBOOK", "key-1"))
                .isInstanceOf(BizException.class);
        verify(tasks, never()).insert(any(PetAvatarTask.class));
    }

    @Test
    @DisplayName("网关未配置时拒绝创建，不扣次数不收费")
    void rejectWhenGatewayMissing() {
        ReflectionTestUtils.setField(service, "gatewayUrl", "");
        when(pets.requireOwned(USER, PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(locks.lockPet(PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(tasks.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.create(USER, PET, "STORYBOOK", "key-2"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("免费额度内创建 QUEUED 任务，价格为0")
    void createFreeTask() {
        when(pets.requireOwned(USER, PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(locks.lockPet(PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(tasks.selectOne(any())).thenReturn(null);
        when(tasks.selectCount(any())).thenReturn(0L);
        when(config.getInt("pet.avatar.free.count", 3)).thenReturn(3);

        var view = service.create(USER, PET, "SOFT3D", "key-3");

        verify(tasks).insert(any(PetAvatarTask.class));
        assertThat(view.status()).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("付费任务在价格未配置（0）时拒绝下单")
    void rejectPaidWhenPriceMissing() {
        when(pets.requireOwned(USER, PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(locks.lockPet(PET)).thenReturn(ownedPet("https://a/p.jpg"));
        when(tasks.selectOne(any())).thenReturn(null);
        // 第一次为待完成任务检查（需0），第二次为已用免费额度（已满3次进入付费分支）
        when(tasks.selectCount(any())).thenReturn(0L, 3L);
        when(config.getInt("pet.avatar.free.count", 3)).thenReturn(3);
        when(config.getLong("pet.avatar.price.cents", 0)).thenReturn(0L);

        assertThatThrownBy(() -> service.create(USER, PET, "WATERCOLOR", "key-4"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("支付单关闭后只允许重建一次，再次重建被拒绝")
    void paymentRebuildLimitedOnce() {
        PetAvatarTask task = new PetAvatarTask();
        task.setId(77L);
        task.setUserId(USER);
        task.setStatus("WAIT_PAY");
        task.setOrderNo("OLD-NO");
        task.setPayRebuildCount(PetAvatarService.MAX_PAYMENT_REBUILDS);
        when(tasks.lock(77L)).thenReturn(task);

        PayOrder closed = new PayOrder();
        closed.setStatus("CLOSED");
        when(orders.selectOne(any())).thenReturn(closed);

        assertThatThrownBy(() -> service.pay(USER, 77L)).isInstanceOf(BizException.class);
        verify(payments, never()).createOrder(anyLong(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("非 WAIT_PAY 状态任务不能付款")
    void payOnlyWhenWaiting() {
        PetAvatarTask task = new PetAvatarTask();
        task.setId(78L);
        task.setUserId(USER);
        task.setStatus("QUEUED");
        when(tasks.lock(78L)).thenReturn(task);

        assertThatThrownBy(() -> service.pay(USER, 78L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("首次付款创建支付单并发起支付")
    void firstPayCreatesOrder() {
        PetAvatarTask task = new PetAvatarTask();
        task.setId(79L);
        task.setUserId(USER);
        task.setStatus("WAIT_PAY");
        task.setPrice(500L);
        when(tasks.lock(79L)).thenReturn(task);
        PayOrder order = new PayOrder();
        order.setOutTradeNo("NEW-NO");
        when(payments.createOrder(anyLong(), any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(order);
        when(tasks.updateById(any(PetAvatarTask.class))).thenReturn(1);
        when(payments.initiate(order)).thenReturn(new PayInitVO());

        service.pay(USER, 79L);

        verify(payments).initiate(order);
        assertThat(task.getOrderNo()).isEqualTo("NEW-NO");
    }
}
