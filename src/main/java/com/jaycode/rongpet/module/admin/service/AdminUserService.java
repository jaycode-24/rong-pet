package com.jaycode.rongpet.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jaycode.rongpet.common.api.PageResult;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.module.admin.dto.UserAdminVO;
import com.jaycode.rongpet.module.admin.dto.UserRiskVO;
import com.jaycode.rongpet.module.admin.entity.AdminAuditLog;
import com.jaycode.rongpet.module.admin.mapper.AdminAuditLogMapper;
import com.jaycode.rongpet.module.fund.entity.FundFlow;
import com.jaycode.rongpet.module.fund.mapper.FundFlowMapper;
import com.jaycode.rongpet.module.message.service.MessageService;
import com.jaycode.rongpet.module.pay.entity.PayOrder;
import com.jaycode.rongpet.module.pay.mapper.PayOrderMapper;
import com.jaycode.rongpet.module.post.constant.PostStatus;
import com.jaycode.rongpet.module.post.constant.RewardStatus;
import com.jaycode.rongpet.module.post.dto.PostCardVO;
import com.jaycode.rongpet.module.post.entity.Post;
import com.jaycode.rongpet.module.post.mapper.PostMapper;
import com.jaycode.rongpet.module.report.entity.Report;
import com.jaycode.rongpet.module.report.mapper.ReportMapper;
import com.jaycode.rongpet.security.StpUserUtil;
import com.jaycode.rongpet.security.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 后台用户管理：查询、处置（限制发帖/限制交易/封禁/冻结/恢复）与风控专题。
 * 敏感字段仅脱敏展示，处置动作全部写审计与站内消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int RISK_LIMIT = 200;

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final ReportMapper reportMapper;
    private final PayOrderMapper payOrderMapper;
    private final FundFlowMapper fundFlowMapper;
    private final AdminAuditLogMapper adminAuditLogMapper;
    private final CryptoService cryptoService;
    private final MessageService messageService;
    private final AdminAuditService adminAuditService;

    // ------------------------------------------------------------------ 查询

    public PageResult<UserAdminVO> page(String status, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .orderByDesc(User::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            if (PHONE.matcher(value).matches()) {
                wrapper.eq(User::getPhoneHmac, cryptoService.hmacIndex(value));
            } else if (value.matches("^\\d{1,19}$")) {
                wrapper.and(w -> w.eq(User::getId, Long.parseLong(value)).or().like(User::getNickname, value));
            } else {
                wrapper.like(User::getNickname, value);
            }
        }
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public UserAdminVO detail(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        UserAdminVO vo = toVO(user);
        vo.setPostCount(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)));
        vo.setReportCount(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, "USER")
                .eq(Report::getTargetId, userId)));

        // 最近帖子（含全部状态，便于审核与风控排查）
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .orderByDesc(Post::getCreateTime)
                .last("limit 10"));
        vo.setRecentPosts(posts.stream().map(this::toCard).toList());

        // 最近支付单：金额均为分，不含任何支付凭证敏感信息
        vo.setRecentOrders(payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getUserId, userId)
                .orderByDesc(PayOrder::getCreateTime)
                .last("limit 10")));

        // 最近资金流水
        vo.setRecentFundFlows(fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getUserId, userId)
                .orderByDesc(FundFlow::getCreateTime)
                .last("limit 10")));

        // 最近被举报记录
        vo.setRecentReports(reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, "USER")
                .eq(Report::getTargetId, userId)
                .orderByDesc(Report::getCreateTime)
                .last("limit 10")));

        // 被处置历史（后台对该用户的账号状态操作）
        vo.setPunishmentHistory(adminAuditLogMapper.selectList(new LambdaQueryWrapper<AdminAuditLog>()
                .eq(AdminAuditLog::getTargetType, "USER")
                .eq(AdminAuditLog::getTargetId, userId)
                .orderByDesc(AdminAuditLog::getCreateTime)
                .last("limit 20")));
        return vo;
    }

    // ------------------------------------------------------------------ 处置

    @Transactional
    public void action(long userId, String action, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        String normalized = StringUtils.hasText(action) ? action.trim().toUpperCase() : "";
        String target = switch (normalized) {
            case "LIMIT_POST" -> UserStatus.LIMIT_POST;
            case "LIMIT_TRADE" -> UserStatus.LIMIT_TRADE;
            case "BAN" -> UserStatus.BANNED;
            case "FROZEN" -> UserStatus.FROZEN;
            case "UNBAN", "RESTORE", "NORMAL" -> UserStatus.NORMAL;
            default -> throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的处置动作：" + action);
        };
        userMapper.update(null, new UpdateWrapper<User>()
                .set("status", target)
                .eq("id", userId));

        if (UserStatus.BANNED.equals(target) || UserStatus.FROZEN.equals(target)) {
            try {
                StpUserUtil.stpLogic.logout(userId);
            } catch (Exception e) {
                log.warn("踢出用户会话失败 userId={}", userId, e);
            }
        }
        String actionText = switch (normalized) {
            case "LIMIT_POST" -> "账号已被限制发帖";
            case "LIMIT_TRADE" -> "账号已被限制交易";
            case "BAN" -> "账号已被封禁";
            case "FROZEN" -> "账号已被冻结";
            default -> "账号已恢复正常";
        };
        messageService.send(userId, MessageService.TYPE_SYSTEM, "账号状态变更",
                actionText + (StringUtils.hasText(reason) ? "，原因：" + reason : ""), "USER", String.valueOf(userId));
        adminAuditService.record("USER_" + normalized, "USER", userId,
                "status=" + user.getStatus() + " -> " + target + (StringUtils.hasText(reason) ? ", reason=" + reason : ""));
    }

    // ------------------------------------------------------------------ 风控专题

    /**
     * 风控专题：同一手机号/身份证多账号、单日批量发帖、被举报用户。
     * 仅做信号聚合，是否处置由运营人工判断。
     */
    public List<UserRiskVO> risks() {
        Map<Long, Set<String>> tags = new LinkedHashMap<>();

        Set<String> phoneDups = duplicateHmac("phone_hmac");
        if (!phoneDups.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getPhoneHmac, phoneDups))
                    .forEach(u -> tags.computeIfAbsent(u.getId(), k -> new LinkedHashSet<>()).add("同手机号多账号"));
        }
        Set<String> idCardDups = duplicateHmac("id_card_hmac");
        if (!idCardDups.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getIdCardHmac, idCardDups))
                    .forEach(u -> tags.computeIfAbsent(u.getId(), k -> new LinkedHashSet<>()).add("同身份证多账号"));
        }

        Map<Long, Long> todayPosts = todayPostCounts();
        todayPosts.forEach((userId, count) -> {
            if (count >= 5) {
                tags.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add("单日批量发帖");
            }
        });
        postMapper.selectMaps(new QueryWrapper<Post>()
                        .select("user_id AS userId")
                        .eq("reward_status", RewardStatus.FROZEN)
                        .in("status", PostStatus.REWARD_FROZEN)
                        .groupBy("user_id")
                        .having("COUNT(*) >= 2"))
                .forEach(row -> tags.computeIfAbsent(asLong(row.get("userId")), k -> new LinkedHashSet<>())
                        .add("多笔在途悬赏"));

        List<UserRiskVO> result = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> entry : tags.entrySet()) {
            if (result.size() >= RISK_LIMIT) {
                break;
            }
            User user = userMapper.selectById(entry.getKey());
            if (user == null) {
                continue;
            }
            UserRiskVO vo = new UserRiskVO();
            vo.setUserId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setMaskedPhone(CryptoService.maskPhone(cryptoService.decrypt(user.getPhoneEnc())));
            vo.setStatus(user.getStatus());
            vo.setRiskTags(new ArrayList<>(entry.getValue()));
            vo.setTodayPostCount(todayPosts.getOrDefault(user.getId(), 0L));
            vo.setRewardInFlight(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                    .eq(Post::getUserId, user.getId())
                    .eq(Post::getStatus, PostStatus.REWARD_FROZEN)
                    .eq(Post::getRewardStatus, RewardStatus.FROZEN)));
            vo.setReportCount(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "USER")
                    .eq(Report::getTargetId, user.getId())));
            result.add(vo);
        }
        return result;
    }

    // ------------------------------------------------------------------ 内部

    private Set<String> duplicateHmac(String column) {
        List<Map<String, Object>> rows = userMapper.selectMaps(new QueryWrapper<User>()
                .select(column + " AS hmac", "COUNT(*) AS cnt")
                .isNotNull(column)
                .ne(column, "")
                .groupBy(column)
                .having("COUNT(*) > 1"));
        Set<String> result = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("hmac");
            if (value == null) {
                value = row.get(column);
            }
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    private Map<Long, Long> todayPostCounts() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<Map<String, Object>> rows = postMapper.selectMaps(new QueryWrapper<Post>()
                .select("user_id AS userId", "COUNT(*) AS cnt")
                .ge("create_time", start)
                .groupBy("user_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object userId = row.get("userId");
            if (userId != null) {
                result.put(asLong(userId), asLong(row.get("cnt")));
            }
        }
        return result;
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private PostCardVO toCard(Post post) {
        PostCardVO vo = new PostCardVO();
        vo.setId(post.getId());
        vo.setType(post.getType());
        vo.setTitle(post.getTitle());
        vo.setPetType(post.getPetType());
        vo.setBreed(post.getBreed());
        vo.setDistrict(post.getDistrict());
        vo.setEventTime(post.getEventTime());
        vo.setRewardAmount(post.getRewardAmount());
        vo.setCoverUrl(post.getCoverUrl());
        vo.setStatus(post.getStatus());
        vo.setTopExpireTime(post.getTopExpireTime());
        vo.setClueCount(post.getClueCount());
        vo.setDescription(post.getDescription());
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }

    private UserAdminVO toVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setMaskedPhone(CryptoService.maskPhone(cryptoService.decrypt(user.getPhoneEnc())));
        vo.setRealNameMasked(maskName(cryptoService.decrypt(user.getRealNameEnc())));
        vo.setIdCardMasked(user.getIdCardEnc() == null ? null
                : CryptoService.maskIdCard(cryptoService.decrypt(user.getIdCardEnc())));
        vo.setStatus(user.getStatus());
        vo.setCity(user.getCity());
        vo.setRegisterTime(user.getRegisterTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setBanned(UserStatus.BANNED.equals(user.getStatus()) || UserStatus.FROZEN.equals(user.getStatus()));
        return vo;
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.length() <= 1 ? name : name.charAt(0) + "**";
    }
}
