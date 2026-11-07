package com.jaycode.rongpet.module.feeder.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.feeder.dto.FeederApplyRequest;
import com.jaycode.rongpet.module.feeder.dto.FeederApplyVO;
import com.jaycode.rongpet.module.feeder.dto.FeederCardVO;
import com.jaycode.rongpet.module.feeder.dto.FeederProfileRequest;
import com.jaycode.rongpet.module.feeder.entity.FeederApply;
import com.jaycode.rongpet.module.feeder.entity.FeederProfile;
import com.jaycode.rongpet.module.feeder.error.FeederErrorCode;
import com.jaycode.rongpet.module.feeder.mapper.FeederApplyMapper;
import com.jaycode.rongpet.module.feeder.mapper.FeederProfileMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.post.constant.ChengduDistrict;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 喂养员服务（信息模式）：
 * 实名认证形式核验 → 三步资料完善 → 列表展示 → 接收喂养请求。
 * 平台不经手任何喂养资金，认证不代表平台对服务质量的实质担保。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeederService {

    private static final int CERT_MONTHS = 12;
    private static final long MAX_PRICE_CENTS = 10_000_000L;

    private final FeederProfileMapper profileMapper;
    private final FeederApplyMapper applyMapper;
    private final UserMapper userMapper;
    private final CryptoService cryptoService;
    private final MessageService messageService;

    // ------------------------------------------------------------------ 认证申请

    @Transactional
    public FeederApplyVO apply(long userId, FeederApplyRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null || UserStatus.BANNED.equals(user.getStatus()) || UserStatus.FROZEN.equals(user.getStatus())) {
            throw new BizException(FeederErrorCode.USER_FORBIDDEN);
        }
        if (request.getMaterials() == null || request.getMaterials().isEmpty()) {
            throw new BizException(FeederErrorCode.MATERIAL_REQUIRED);
        }
        if (!ChengduDistrict.isValid(request.getDistrict())) {
            throw new BizException(FeederErrorCode.DISTRICT_INVALID);
        }
        long pending = applyMapper.selectCount(new LambdaQueryWrapper<FeederApply>()
                .eq(FeederApply::getUserId, userId)
                .eq(FeederApply::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BizException(FeederErrorCode.APPLY_PENDING);
        }
        FeederApply apply = new FeederApply();
        apply.setUserId(userId);
        apply.setRealNameEnc(cryptoService.encrypt(request.getRealName()));
        apply.setIdCardEnc(cryptoService.encrypt(request.getIdCard()));
        apply.setIdCardHmac(cryptoService.hmacIndex(request.getIdCard()));
        apply.setPhoneEnc(cryptoService.encrypt(request.getPhone()));
        apply.setPhoneHmac(cryptoService.hmacIndex(request.getPhone()));
        apply.setDistrict(request.getDistrict());
        apply.setMaterials(JSONUtil.toJsonStr(request.getMaterials()));
        apply.setStatus("PENDING");
        applyMapper.insert(apply);

        messageService.send(userId, MessageService.TYPE_AUDIT, "认证申请已提交",
                "平台将在1个工作日内完成形式核验，请留意审核结果", "FEEDER", String.valueOf(apply.getId()));
        return toApplyVO(apply);
    }

    public FeederApplyVO myApply(long userId) {
        FeederApply apply = applyMapper.selectOne(new LambdaQueryWrapper<FeederApply>()
                .eq(FeederApply::getUserId, userId)
                .orderByDesc(FeederApply::getCreateTime)
                .last("limit 1"));
        return apply == null ? null : toApplyVO(apply);
    }

    public PageResult<FeederApplyVO> adminApplyPage(String status, int pageNum, int pageSize) {
        Page<FeederApply> page = applyMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FeederApply>()
                        .eq(StringUtils.hasText(status), FeederApply::getStatus, status)
                        .orderByAsc(FeederApply::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(this::toApplyVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public void auditApply(long adminId, long applyId, boolean approve, String remark) {
        FeederApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BizException(FeederErrorCode.APPLY_NOT_FOUND);
        }
        if (!"PENDING".equals(apply.getStatus())) {
            throw new BizException(FeederErrorCode.APPLY_STATUS_INVALID);
        }
        FeederApply update = new FeederApply();
        update.setId(applyId);
        update.setStatus(approve ? "APPROVED" : "REJECTED");
        update.setRejectReason(approve ? null : remark);
        update.setAuditAdminId(adminId);
        update.setAuditTime(LocalDateTime.now());
        applyMapper.updateById(update);

        if (approve) {
            FeederProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<FeederProfile>()
                    .eq(FeederProfile::getUserId, apply.getUserId()));
            boolean isNew = profile == null;
            if (isNew) {
                profile = new FeederProfile();
                profile.setUserId(apply.getUserId());
                profile.setConnectCount(0);
                profile.setProfileComplete(0);
                profile.setDepositAccepted(0);
            }
            profile.setRealNameEnc(apply.getRealNameEnc());
            profile.setIdCardEnc(apply.getIdCardEnc());
            profile.setIdCardHmac(apply.getIdCardHmac());
            profile.setPhoneEnc(apply.getPhoneEnc());
            profile.setPhoneHmac(apply.getPhoneHmac());
            profile.setDistrict(apply.getDistrict());
            profile.setStatus("ACTIVE");
            profile.setCertExpireTime(LocalDateTime.now().plusMonths(CERT_MONTHS));
            if (isNew) {
                profileMapper.insert(profile);
            } else {
                profileMapper.updateById(profile);
            }
            messageService.send(apply.getUserId(), MessageService.TYPE_AUDIT, "喂养员认证通过",
                    "认证已通过，请完善服务介绍、时段与参考价格后即可对外展示", "FEEDER", String.valueOf(applyId));
        } else {
            messageService.send(apply.getUserId(), MessageService.TYPE_AUDIT, "喂养员认证未通过",
                    "原因：" + (StringUtils.hasText(remark) ? remark : "材料不足")
                            + "，可补齐材料后重新提交", "FEEDER", String.valueOf(applyId));
        }
    }

    // ------------------------------------------------------------------ 资料

    public FeederCardVO myProfile(long userId) {
        FeederProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<FeederProfile>()
                .eq(FeederProfile::getUserId, userId));
        return profile == null ? null : toCard(profile);
    }

    @Transactional
    public FeederCardVO updateProfile(long userId, FeederProfileRequest request) {
        FeederProfile profile = requireByUser(userId);
        if ("BANNED".equals(profile.getStatus())) {
            throw new BizException(FeederErrorCode.PROFILE_STATUS_INVALID);
        }
        if (request.getServiceDistricts() != null) {
            if (request.getServiceDistricts().isEmpty()
                    || request.getServiceDistricts().stream().anyMatch(d -> !ChengduDistrict.isValid(d))) {
                throw new BizException(FeederErrorCode.DISTRICT_INVALID);
            }
            profile.setServiceDistricts(JSONUtil.toJsonStr(request.getServiceDistricts()));
        }
        if (request.getServiceIntro() != null) {
            String intro = request.getServiceIntro().trim();
            if (!intro.isEmpty() && (intro.length() < 50 || intro.length() > 500)) {
                throw new BizException(FeederErrorCode.INTRO_LENGTH_INVALID);
            }
            profile.setServiceIntro(intro);
        }
        if (request.getServiceTimes() != null) {
            profile.setServiceTimes(request.getServiceTimes().isEmpty()
                    ? null : JSONUtil.toJsonStr(request.getServiceTimes()));
        }
        if (request.getPriceOnce() != null) {
            validatePrice(request.getPriceOnce());
            profile.setPriceOnce(request.getPriceOnce());
        }
        if (request.getPriceDaily() != null) {
            validatePrice(request.getPriceDaily());
            profile.setPriceDaily(request.getPriceDaily());
        }
        if (request.getDepositAccepted() != null) {
            profile.setDepositAccepted(request.getDepositAccepted() ? 1 : 0);
        }
        boolean complete = StringUtils.hasText(profile.getServiceIntro())
                && profile.getServiceIntro().length() >= 50
                && StringUtils.hasText(profile.getServiceTimes())
                && (profile.getPriceOnce() != null || profile.getPriceDaily() != null);
        profile.setProfileComplete(complete ? 1 : 0);
        if (!"PAUSED".equals(profile.getStatus()) && certValid(profile)) {
            profile.setStatus("ACTIVE");
        }
        profileMapper.updateById(profile);
        return toCard(profileMapper.selectById(profile.getId()));
    }

    @Transactional
    public void pause(long userId) {
        FeederProfile profile = requireByUser(userId);
        FeederProfile update = new FeederProfile();
        update.setId(profile.getId());
        update.setStatus("PAUSED");
        profileMapper.updateById(update);
    }

    @Transactional
    public void resume(long userId) {
        FeederProfile profile = requireByUser(userId);
        if (!certValid(profile)) {
            throw new BizException(FeederErrorCode.PROFILE_STATUS_INVALID, "认证已过期，请重新提交认证");
        }
        FeederProfile update = new FeederProfile();
        update.setId(profile.getId());
        update.setStatus("ACTIVE");
        profileMapper.updateById(update);
    }

    // ------------------------------------------------------------------ 列表与详情

    public PageResult<FeederCardVO> list(String district, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<FeederProfile> wrapper = new LambdaQueryWrapper<FeederProfile>()
                .eq(FeederProfile::getStatus, "ACTIVE")
                .eq(FeederProfile::getProfileComplete, 1)
                .gt(FeederProfile::getCertExpireTime, LocalDateTime.now());
        if (StringUtils.hasText(district)) {
            wrapper.and(w -> w.eq(FeederProfile::getDistrict, district)
                    .or().like(FeederProfile::getServiceDistricts, district));
        }
        if (StringUtils.hasText(keyword)) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getNickname, keyword.trim()))
                    .stream().map(User::getId).toList();
            if (userIds.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
            wrapper.in(FeederProfile::getUserId, userIds);
        }
        wrapper.orderByDesc(FeederProfile::getLastActiveTime)
                .orderByDesc(FeederProfile::getConnectCount);
        Page<FeederProfile> page = profileMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toCard).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public FeederCardVO detail(long profileId) {
        FeederProfile profile = profileMapper.selectById(profileId);
        if (profile == null || !"ACTIVE".equals(profile.getStatus()) || !certValid(profile)) {
            throw new BizException(FeederErrorCode.PROFILE_NOT_FOUND);
        }
        return toCard(profile);
    }

    public PageResult<FeederCardVO> adminProfilePage(String status, int pageNum, int pageSize) {
        Page<FeederProfile> page = profileMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FeederProfile>()
                        .eq(StringUtils.hasText(status), FeederProfile::getStatus, status)
                        .orderByAsc(FeederProfile::getCreateTime));
        return PageResult.of(page.getRecords().stream().map(this::toCard).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 暂停接单 / 封禁 / 恢复；BAN 后不可再接收请求 */
    @Transactional
    public void adminAction(long profileId, String action, String reason) {
        FeederProfile profile = profileMapper.selectById(profileId);
        if (profile == null) {
            throw new BizException(FeederErrorCode.PROFILE_NOT_FOUND);
        }
        String target = switch (action) {
            case "PAUSE" -> "PAUSED";
            case "BAN" -> "BANNED";
            case "RESUME" -> certValid(profile) ? "ACTIVE" : "EXPIRED";
            default -> throw new BizException(FeederErrorCode.PROFILE_STATUS_INVALID);
        };
        FeederProfile update = new FeederProfile();
        update.setId(profileId);
        update.setStatus(target);
        profileMapper.updateById(update);
        messageService.send(profile.getUserId(), MessageService.TYPE_AUDIT, "喂养员状态变更",
                "当前状态：" + target + (StringUtils.hasText(reason) ? "，原因：" + reason : ""),
                "FEEDER", String.valueOf(profileId));
    }

    // ------------------------------------------------------------------ 内部供喂养模块调用

    public FeederProfile requireActiveProfile(long userId) {
        FeederProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<FeederProfile>()
                .eq(FeederProfile::getUserId, userId));
        if (profile == null) {
            throw new BizException(FeederErrorCode.PROFILE_NOT_CERTIFIED);
        }
        if (!"ACTIVE".equals(profile.getStatus()) || !certValid(profile)) {
            throw new BizException(FeederErrorCode.PROFILE_STATUS_INVALID);
        }
        return profile;
    }

    @Transactional
    public void incrementConnect(long userId) {
        profileMapper.update(null, new UpdateWrapper<FeederProfile>()
                .setSql("connect_count = connect_count + 1")
                .set("last_active_time", LocalDateTime.now())
                .eq("user_id", userId));
    }

    public void touch(long userId) {
        profileMapper.update(null, new UpdateWrapper<FeederProfile>()
                .set("last_active_time", LocalDateTime.now())
                .eq("user_id", userId));
    }

    // ------------------------------------------------------------------ 定时任务

    @Transactional
    public int expireCertifications() {
        List<FeederProfile> expired = profileMapper.selectList(new LambdaQueryWrapper<FeederProfile>()
                .in(FeederProfile::getStatus, "ACTIVE", "PAUSED")
                .isNotNull(FeederProfile::getCertExpireTime)
                .lt(FeederProfile::getCertExpireTime, LocalDateTime.now())
                .last("limit 200"));
        for (FeederProfile profile : expired) {
            FeederProfile update = new FeederProfile();
            update.setId(profile.getId());
            update.setStatus("EXPIRED");
            profileMapper.updateById(update);
            messageService.send(profile.getUserId(), MessageService.TYPE_SYSTEM, "喂养员认证已过期",
                    "认证已过期，资料已从列表隐藏，请重新提交认证材料", "FEEDER", String.valueOf(profile.getId()));
        }
        return expired.size();
    }

    /** 到期前 30 天提醒（按日任务，仅在整 30 天当天命中一次） */
    @Transactional
    public int remindCertExpiring() {
        LocalDateTime start = LocalDate.now().plusDays(30).atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<FeederProfile> profiles = profileMapper.selectList(new LambdaQueryWrapper<FeederProfile>()
                .in(FeederProfile::getStatus, "ACTIVE", "PAUSED")
                .between(FeederProfile::getCertExpireTime, start, end));
        for (FeederProfile profile : profiles) {
            messageService.send(profile.getUserId(), MessageService.TYPE_SYSTEM, "喂养员认证即将到期",
                    "认证将在30天后到期，请及时更新材料，避免资料隐藏", "FEEDER", String.valueOf(profile.getId()));
        }
        return profiles.size();
    }

    // ------------------------------------------------------------------ 转换

    public FeederCardVO toCard(FeederProfile profile) {
        FeederCardVO vo = new FeederCardVO();
        vo.setId(profile.getId());
        vo.setUserId(profile.getUserId());
        User user = userMapper.selectById(profile.getUserId());
        if (user != null) {
            vo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : "喂养员");
            vo.setAvatarUrl(user.getAvatarUrl());
        }
        vo.setDistrict(profile.getDistrict());
        vo.setServiceDistricts(parseStringList(profile.getServiceDistricts()));
        vo.setServiceIntro(profile.getServiceIntro());
        vo.setServiceTimes(parseMapList(profile.getServiceTimes()));
        vo.setPriceOnce(profile.getPriceOnce());
        vo.setPriceDaily(profile.getPriceDaily());
        vo.setDepositAccepted(profile.getDepositAccepted() != null && profile.getDepositAccepted() == 1);
        vo.setConnectCount(profile.getConnectCount() == null ? 0 : profile.getConnectCount());
        vo.setCertified("ACTIVE".equals(profile.getStatus()) && certValid(profile));
        vo.setStatus(profile.getStatus());
        vo.setProfileComplete(profile.getProfileComplete() != null && profile.getProfileComplete() == 1);
        vo.setCertExpireTime(profile.getCertExpireTime());
        vo.setLastActiveTime(profile.getLastActiveTime());
        return vo;
    }

    public List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseMapList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map> raw = JSONUtil.parseArray(json).toList(Map.class);
            return raw.stream()
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private FeederApplyVO toApplyVO(FeederApply apply) {
        FeederApplyVO vo = new FeederApplyVO();
        vo.setId(apply.getId());
        vo.setUserId(apply.getUserId());
        User user = userMapper.selectById(apply.getUserId());
        vo.setNickname(user == null ? null : user.getNickname());
        vo.setRealNameMasked(maskName(cryptoService.decrypt(apply.getRealNameEnc())));
        vo.setIdCardMasked(CryptoService.maskIdCard(cryptoService.decrypt(apply.getIdCardEnc())));
        vo.setPhoneMasked(CryptoService.maskPhone(cryptoService.decrypt(apply.getPhoneEnc())));
        vo.setDistrict(apply.getDistrict());
        vo.setMaterials(parseStringList(apply.getMaterials()));
        vo.setStatus(apply.getStatus());
        vo.setRejectReason(apply.getRejectReason());
        vo.setAuditTime(apply.getAuditTime());
        vo.setCreateTime(apply.getCreateTime());
        return vo;
    }

    private FeederProfile requireByUser(long userId) {
        FeederProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<FeederProfile>()
                .eq(FeederProfile::getUserId, userId));
        if (profile == null) {
            throw new BizException(FeederErrorCode.PROFILE_NOT_FOUND);
        }
        return profile;
    }

    private void validatePrice(long price) {
        if (price < 0 || price > MAX_PRICE_CENTS) {
            throw new BizException(FeederErrorCode.PRICE_INVALID);
        }
    }

    private boolean certValid(FeederProfile profile) {
        return profile.getCertExpireTime() != null
                && profile.getCertExpireTime().isAfter(LocalDateTime.now());
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.length() <= 1 ? name : name.charAt(0) + "**";
    }
}
