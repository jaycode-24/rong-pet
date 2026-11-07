package com.jaycode.rongpet.module.feeding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.feeder.entity.FeederProfile;
import com.jaycode.rongpet.module.feeder.service.FeederService;
import com.jaycode.rongpet.module.feeding.dto.FeedingCreateRequest;
import com.jaycode.rongpet.module.feeding.dto.FeedingVO;
import com.jaycode.rongpet.module.feeding.entity.FeedingRequest;
import com.jaycode.rongpet.module.feeding.error.FeedingErrorCode;
import com.jaycode.rongpet.module.feeding.mapper.FeedingRequestMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.pet.entity.Pet;
import com.jaycode.rongpet.module.pet.mapper.PetMapper;
import com.jaycode.rongpet.module.pet.service.PetService;
import com.jaycode.rongpet.module.post.constant.ChengduDistrict;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 上门喂养请求服务（纯信息模式）。
 * 状态机：PENDING → CONTACTED → COMPLETED；PENDING/CONTACTED → CLOSED。
 * 48 小时未响应自动关闭；全程无支付、无托管、无佣金。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedingService {

    private static final String DISCLAIMER = "平台仅提供信息对接服务，交易与责任由双方自行承担。";
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final FeedingRequestMapper feedingMapper;
    private final UserMapper userMapper;
    private final PetService petService;
    private final PetMapper petMapper;
    private final FeederService feederService;
    private final CryptoService cryptoService;
    private final MessageService messageService;

    // ------------------------------------------------------------------ 发布

    @Transactional
    public FeedingVO create(long userId, FeedingCreateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null || UserStatus.BANNED.equals(user.getStatus())
                || UserStatus.FROZEN.equals(user.getStatus())
                || UserStatus.LIMIT_TRADE.equals(user.getStatus())) {
            throw new BizException(FeedingErrorCode.USER_FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(request.getAgreed())) {
            throw new BizException(FeedingErrorCode.AGREEMENT_REQUIRED);
        }
        if (!ChengduDistrict.isValid(request.getDistrict())) {
            throw new BizException(FeedingErrorCode.DISTRICT_INVALID);
        }
        if (!"ONCE".equals(request.getServiceType()) && !"MULTI_DAY".equals(request.getServiceType())) {
            throw new BizException(FeedingErrorCode.SERVICE_TYPE_INVALID);
        }
        if (request.getStartTime() == null || request.getStartTime().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new BizException(FeedingErrorCode.TIME_INVALID);
        }
        if ("MULTI_DAY".equals(request.getServiceType())
                && (request.getEndTime() == null || request.getEndTime().isBefore(request.getStartTime()))) {
            throw new BizException(FeedingErrorCode.TIME_INVALID);
        }
        if (!PHONE.matcher(request.getContactPhone()).matches()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "请填写正确的11位手机号");
        }
        Pet pet = petService.requireOwned(userId, request.getPetId());
        if (request.getFeederId() != null) {
            FeederProfile profile = feederService.requireActiveProfile(request.getFeederId());
            if (!serves(profile, request.getDistrict())) {
                throw new BizException(FeedingErrorCode.DISTRICT_NOT_SERVED);
            }
        }

        FeedingRequest feeding = new FeedingRequest();
        feeding.setUserId(userId);
        feeding.setFeederId(request.getFeederId());
        feeding.setPetId(pet.getId());
        feeding.setServiceType(request.getServiceType());
        feeding.setStartTime(request.getStartTime());
        feeding.setEndTime(request.getEndTime());
        feeding.setDailyTime(request.getDailyTime());
        feeding.setDistrict(request.getDistrict());
        feeding.setAddressEnc(cryptoService.encrypt(request.getAddress()));
        feeding.setContactName(request.getContactName());
        feeding.setContactEnc(cryptoService.encrypt(request.getContactPhone()));
        feeding.setContactHmac(cryptoService.hmacIndex(request.getContactPhone()));
        feeding.setPetNote(StringUtils.hasText(request.getPetNote())
                ? request.getPetNote()
                : truncate(pet.getName() + "（" + pet.getBreed() + "）", 200));
        feeding.setSpecialRequire(request.getSpecialRequire());
        feeding.setStatus("PENDING");
        feedingMapper.insert(feeding);

        if (request.getFeederId() != null) {
            messageService.send(request.getFeederId(), MessageService.TYPE_ORDER, "收到新的喂养请求",
                    "请求编号 " + feeding.getId() + "，服务时间 " + feeding.getStartTime()
                            + "，请在48小时内响应", "FEEDING", String.valueOf(feeding.getId()));
        }
        messageService.send(userId, MessageService.TYPE_ORDER, "喂养请求已提交",
                "请求已进入待响应状态，48小时内未获响应将自动关闭", "FEEDING", String.valueOf(feeding.getId()));
        return toVO(feeding, true);
    }

    // ------------------------------------------------------------------ 查询

    public PageResult<FeedingVO> mine(long userId, String status, int pageNum, int pageSize) {
        Page<FeedingRequest> page = feedingMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FeedingRequest>()
                        .eq(FeedingRequest::getUserId, userId)
                        .eq(StringUtils.hasText(status), FeedingRequest::getStatus, status)
                        .orderByDesc(FeedingRequest::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(f -> toVO(f, true)).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 喂养员视图：定向请求 + 服务区县内的公开请求 */
    public PageResult<FeedingVO> feederPage(long viewerId, String status, int pageNum, int pageSize) {
        FeederProfile profile = feederService.requireActiveProfile(viewerId);
        List<String> districts = servedDistricts(profile);
        LambdaQueryWrapper<FeedingRequest> wrapper = new LambdaQueryWrapper<>();
        if (!StringUtils.hasText(status)) {
            wrapper.and(a -> a.eq(FeedingRequest::getFeederId, viewerId)
                    .or(b -> b.isNull(FeedingRequest::getFeederId)
                            .eq(FeedingRequest::getStatus, "PENDING")
                            .in(FeedingRequest::getDistrict, districts)));
        } else if ("PENDING".equals(status)) {
            wrapper.eq(FeedingRequest::getStatus, "PENDING")
                    .and(a -> a.eq(FeedingRequest::getFeederId, viewerId)
                            .or(b -> b.isNull(FeedingRequest::getFeederId)
                                    .in(FeedingRequest::getDistrict, districts)));
        } else {
            wrapper.eq(FeedingRequest::getFeederId, viewerId)
                    .eq(FeedingRequest::getStatus, status);
        }
        wrapper.orderByDesc(FeedingRequest::getCreateTime);
        Page<FeedingRequest> page = feedingMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream()
                        .map(f -> toVO(f, !"PENDING".equals(f.getStatus()))).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public FeedingVO detail(long viewerId, long feedingId) {
        FeedingRequest feeding = require(feedingId);
        boolean owner = feeding.getUserId() == viewerId;
        boolean assigned = feeding.getFeederId() != null && feeding.getFeederId() == viewerId;
        boolean publicPool = feeding.getFeederId() == null && "PENDING".equals(feeding.getStatus())
                && isActiveFeederInDistrict(viewerId, feeding.getDistrict());
        if (!owner && !assigned && !publicPool) {
            throw new BizException(FeedingErrorCode.NO_PERMISSION);
        }
        boolean full = owner || (assigned && ("CONTACTED".equals(feeding.getStatus())
                || "COMPLETED".equals(feeding.getStatus())));
        return toVO(feeding, full);
    }

    public PageResult<FeedingVO> adminPage(String status, Long userId, Long feederId, Long feedingId,
                                            int pageNum, int pageSize) {
        Page<FeedingRequest> page = feedingMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FeedingRequest>()
                        .eq(StringUtils.hasText(status), FeedingRequest::getStatus, status)
                        .eq(userId != null, FeedingRequest::getUserId, userId)
                        .eq(feederId != null, FeedingRequest::getFeederId, feederId)
                        .eq(feedingId != null, FeedingRequest::getId, feedingId)
                        .orderByDesc(FeedingRequest::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(f -> toVO(f, true)).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 后台详情：可见完整地址与联系方式 */
    public FeedingVO adminDetail(long feedingId) {
        return toVO(require(feedingId), true);
    }

    /** 后台强制关闭：用于纠纷或异常请求 */
    @Transactional
    public void adminClose(long feedingId, String reason) {
        FeedingRequest feeding = require(feedingId);
        if ("CLOSED".equals(feeding.getStatus()) || "COMPLETED".equals(feeding.getStatus())) {
            throw new BizException(FeedingErrorCode.STATUS_INVALID);
        }
        FeedingRequest update = new FeedingRequest();
        update.setId(feedingId);
        update.setStatus("CLOSED");
        update.setCloseReason(StringUtils.hasText(reason) ? reason : "平台关闭");
        feedingMapper.updateById(update);
        messageService.send(feeding.getUserId(), MessageService.TYPE_ORDER, "喂养请求已关闭",
                "请求编号 " + feedingId + " 已被平台关闭"
                        + (StringUtils.hasText(reason) ? "：" + reason : ""), "FEEDING", String.valueOf(feedingId));
        if (feeding.getFeederId() != null) {
            messageService.send(feeding.getFeederId(), MessageService.TYPE_ORDER, "喂养请求已关闭",
                    "请求编号 " + feedingId + " 已被平台关闭", "FEEDING", String.valueOf(feedingId));
        }
    }

    // ------------------------------------------------------------------ 状态流转

    @Transactional
    public void markContacted(long viewerId, long feedingId) {
        FeedingRequest feeding = require(feedingId);
        if (!"PENDING".equals(feeding.getStatus())) {
            throw new BizException(FeedingErrorCode.STATUS_INVALID);
        }
        FeederProfile profile = feederService.requireActiveProfile(viewerId);
        if (feeding.getFeederId() != null && feeding.getFeederId() != viewerId) {
            throw new BizException(FeedingErrorCode.NO_PERMISSION);
        }
        if (feeding.getFeederId() == null) {
            if (!serves(profile, feeding.getDistrict())) {
                throw new BizException(FeedingErrorCode.DISTRICT_NOT_SERVED);
            }
        }
        FeedingRequest update = new FeedingRequest();
        update.setId(feedingId);
        update.setFeederId(viewerId);
        update.setStatus("CONTACTED");
        update.setContactTime(LocalDateTime.now());
        feedingMapper.updateById(update);
        feederService.touch(viewerId);

        messageService.send(feeding.getUserId(), MessageService.TYPE_ORDER, "喂养员已响应",
                "喂养员已标记联系，双方可查看联系方式并线下沟通", "FEEDING", String.valueOf(feedingId));
    }

    @Transactional
    public void complete(long viewerId, long feedingId) {
        FeedingRequest feeding = require(feedingId);
        boolean owner = feeding.getUserId() == viewerId;
        boolean feeder = feeding.getFeederId() != null && feeding.getFeederId() == viewerId;
        if (!owner && !feeder) {
            throw new BizException(FeedingErrorCode.ONLY_PARTICIPANT);
        }
        if (!"CONTACTED".equals(feeding.getStatus())) {
            throw new BizException(FeedingErrorCode.STATUS_INVALID);
        }
        FeedingRequest update = new FeedingRequest();
        update.setId(feedingId);
        update.setStatus("COMPLETED");
        update.setCompleteTime(LocalDateTime.now());
        feedingMapper.updateById(update);
        if (feeding.getFeederId() != null) {
            feederService.incrementConnect(feeding.getFeederId());
        }
        messageService.send(feeding.getUserId(), MessageService.TYPE_ORDER, "喂养已完成",
                "请求编号 " + feedingId + " 已标记完成，感谢使用蓉宠帮", "FEEDING", String.valueOf(feedingId));
        if (feeding.getFeederId() != null) {
            messageService.send(feeding.getFeederId(), MessageService.TYPE_ORDER, "喂养已完成",
                    "请求编号 " + feedingId + " 已标记完成", "FEEDING", String.valueOf(feedingId));
        }
    }

    @Transactional
    public void close(long viewerId, long feedingId, String reason) {
        FeedingRequest feeding = require(feedingId);
        boolean owner = feeding.getUserId() == viewerId;
        boolean feeder = feeding.getFeederId() != null && feeding.getFeederId() == viewerId;
        if (!owner && !feeder) {
            throw new BizException(FeedingErrorCode.ONLY_PARTICIPANT);
        }
        if (!"PENDING".equals(feeding.getStatus()) && !"CONTACTED".equals(feeding.getStatus())) {
            throw new BizException(FeedingErrorCode.STATUS_INVALID);
        }
        FeedingRequest update = new FeedingRequest();
        update.setId(feedingId);
        update.setStatus("CLOSED");
        update.setCloseReason(reason);
        feedingMapper.updateById(update);

        Long notifyUser = owner ? feeding.getFeederId() : feeding.getUserId();
        if (notifyUser != null) {
            messageService.send(notifyUser, MessageService.TYPE_ORDER, "喂养请求已关闭",
                    "请求编号 " + feedingId + " 已关闭" + (StringUtils.hasText(reason) ? "：" + reason : ""),
                    "FEEDING", String.valueOf(feedingId));
        }
    }

    // ------------------------------------------------------------------ 定时任务

    @Transactional
    public int timeoutClose() {
        List<FeedingRequest> list = feedingMapper.selectList(new LambdaQueryWrapper<FeedingRequest>()
                .eq(FeedingRequest::getStatus, "PENDING")
                .lt(FeedingRequest::getCreateTime, LocalDateTime.now().minusHours(48))
                .last("limit 200"));
        for (FeedingRequest feeding : list) {
            FeedingRequest update = new FeedingRequest();
            update.setId(feeding.getId());
            update.setStatus("CLOSED");
            update.setCloseReason("48小时未获响应自动关闭");
            feedingMapper.updateById(update);
            messageService.send(feeding.getUserId(), MessageService.TYPE_ORDER, "喂养请求已关闭",
                    "请求编号 " + feeding.getId() + " 48小时未获响应，已自动关闭，可重新发布",
                    "FEEDING", String.valueOf(feeding.getId()));
        }
        return list.size();
    }

    // ------------------------------------------------------------------ 内部

    private FeedingRequest require(long id) {
        FeedingRequest feeding = feedingMapper.selectById(id);
        if (feeding == null) {
            throw new BizException(FeedingErrorCode.NOT_FOUND);
        }
        return feeding;
    }

    private boolean serves(FeederProfile profile, String district) {
        return servedDistricts(profile).contains(district);
    }

    private List<String> servedDistricts(FeederProfile profile) {
        List<String> list = feederService.parseStringList(profile.getServiceDistricts());
        if (list.isEmpty() && StringUtils.hasText(profile.getDistrict())) {
            return List.of(profile.getDistrict());
        }
        return list;
    }

    private boolean isActiveFeederInDistrict(long viewerId, String district) {
        try {
            return serves(feederService.requireActiveProfile(viewerId), district);
        } catch (BizException e) {
            return false;
        }
    }

    private FeedingVO toVO(FeedingRequest feeding, boolean fullContact) {
        FeedingVO vo = new FeedingVO();
        vo.setId(feeding.getId());
        vo.setUserId(feeding.getUserId());
        vo.setOwnerNickname(nickname(feeding.getUserId()));
        vo.setFeederId(feeding.getFeederId());
        vo.setFeederNickname(feeding.getFeederId() == null ? null : nickname(feeding.getFeederId()));
        vo.setPetId(feeding.getPetId());
        Pet pet = petMapper.selectById(feeding.getPetId());
        vo.setPetName(pet == null ? null : pet.getName());
        vo.setServiceType(feeding.getServiceType());
        vo.setStartTime(feeding.getStartTime());
        vo.setEndTime(feeding.getEndTime());
        vo.setDailyTime(feeding.getDailyTime());
        vo.setDistrict(feeding.getDistrict());
        vo.setAddress(fullContact ? cryptoService.decrypt(feeding.getAddressEnc()) : null);
        vo.setContactName(feeding.getContactName());
        String phone = cryptoService.decrypt(feeding.getContactEnc());
        vo.setContactMasked(fullContact ? phone : CryptoService.maskPhone(phone));
        vo.setPetNote(feeding.getPetNote());
        vo.setSpecialRequire(feeding.getSpecialRequire());
        vo.setStatus(feeding.getStatus());
        vo.setCloseReason(feeding.getCloseReason());
        vo.setContactTime(feeding.getContactTime());
        vo.setCompleteTime(feeding.getCompleteTime());
        vo.setCreateTime(feeding.getCreateTime());
        vo.setDisclaimer(DISCLAIMER);
        return vo;
    }

    private String nickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getNickname()) ? "宠友" : user.getNickname();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
