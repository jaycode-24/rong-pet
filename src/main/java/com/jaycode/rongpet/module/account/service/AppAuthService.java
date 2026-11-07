package com.jaycode.rongpet.module.account.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.module.account.constant.UserStatus;
import com.jaycode.rongpet.module.account.dto.LoginVO;
import com.jaycode.rongpet.module.account.dto.UserVO;
import com.jaycode.rongpet.module.account.dto.WxLoginRequest;
import com.jaycode.rongpet.module.account.entity.User;
import com.jaycode.rongpet.module.account.error.AccountErrorCode;
import com.jaycode.rongpet.module.account.mapper.UserMapper;
import com.jaycode.rongpet.security.StpUserUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import me.chanjar.weixin.common.error.WxErrorException;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 小程序端认证服务：wx.login code 换 openid 并 upsert 用户。
 * <ul>
 *   <li>微信凭证已配置：走真实 code2session</li>
 *   <li>未配置且开启 mock（仅 dev）：按 code 生成确定性 mock openid，便于联调</li>
 *   <li>均未满足：拒绝登录</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthService {

    private final ObjectProvider<WxMaService> wxMaServiceProvider;
    private final UserMapper userMapper;
    private final AppUserVOAssembler assembler;

    /** 仅在无微信凭证且开启 mock 时生效，生产环境凭证齐全后无影响 */
    @Value("${rongpet.wx.ma.mock:false}")
    private boolean wxMockEnabled;

    public LoginVO login(WxLoginRequest request) {
        String appId = resolveOpenid(request.getCode());
        User user = upsertByOpenid(appId);
        ensureLoginable(user);

        StpUserUtil.login(user.getId());
        LoginVO vo = new LoginVO();
        vo.setToken(StpUserUtil.getTokenValue());
        vo.setNew(user.getLastLoginTime() == null);
        vo.setUser(assembler.toVO(user));
        log.info("小程序登录成功 userId={}, mock={}", user.getId(), isMockMode());
        return vo;
    }

    public void logout() {
        StpUserUtil.logout();
    }

    /** 根据登录凭证换取 openid（mock 模式生成确定性假 openid，便于本地联调） */
    private String resolveOpenid(String code) {
        WxMaService wxMaService = wxMaServiceProvider.getIfAvailable();
        if (wxMaService != null) {
            try {
                return wxMaService.getUserService().getSessionInfo(code).getOpenid();
            } catch (WxErrorException e) {
                log.warn("微信 code2session 失败: {}", e.getMessage());
                throw new BizException(CommonErrorCode.REMOTE_CALL_ERROR, "微信登录失败，请重试");
            }
        }
        if (wxMockEnabled) {
            log.warn("微信凭证未配置，使用 mock 登录（仅限开发环境）");
            return "mock_" + SecureUtil.md5(code);
        }
        throw new BizException(AccountErrorCode.WX_NOT_CONFIGURED);
    }

    /** 按 openid upsert 用户。并发首登触发唯一键冲突时重查兜底。 */
    private User upsertByOpenid(String openid) {
        User user = selectByOpenid(openid);
        if (user != null) {
            touchLastLogin(user);
            return user;
        }
        User created = new User();
        created.setOpenId(openid);
        created.setNickname("宠友" + randomSuffix());
        created.setStatus(UserStatus.NORMAL);
        created.setCity("CHENGDU");
        created.setRegisterTime(LocalDateTime.now());
        try {
            userMapper.insert(created);
        } catch (DuplicateKeyException e) {
            user = selectByOpenid(openid);
            if (user == null) {
                throw e;
            }
            touchLastLogin(user);
            return user;
        }
        return selectByOpenid(openid);
    }

    private void touchLastLogin(User user) {
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);
        user.setLastLoginTime(update.getLastLoginTime());
    }

    private User selectByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenId, openid));
    }

    private void ensureLoginable(User user) {
        if (!StringUtils.hasText(user.getStatus())
                || UserStatus.BANNED.equals(user.getStatus())
                || UserStatus.FROZEN.equals(user.getStatus())
                || UserStatus.CANCELLED.equals(user.getStatus())
                || user.getCancelTime() != null) {
            throw new BizException(AccountErrorCode.USER_BANNED);
        }
    }

    private boolean isMockMode() {
        return (wxMaServiceProvider.getIfAvailable() == null) && wxMockEnabled;
    }

    /** 新用户默认昵称后缀 */
    private String randomSuffix() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
