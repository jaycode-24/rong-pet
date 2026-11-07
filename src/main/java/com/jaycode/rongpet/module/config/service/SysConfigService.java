package com.jaycode.rongpet.module.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jaycode.rongpet.common.exception.BizException;
import com.jaycode.rongpet.common.error.CommonErrorCode;
import com.jaycode.rongpet.module.config.entity.SysConfig;
import com.jaycode.rongpet.module.config.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运营配置读取服务：60 秒本地缓存，后台修改后最迟 60 秒生效，单机部署足够。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private static final long CACHE_MILLIS = 60_000L;

    private final SysConfigMapper sysConfigMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0L;

    public String get(String key, String defaultValue) {
        refreshIfNeeded();
        String value = cache.get(key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("配置 {} 非整数，回退默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        return (int) getLong(key, defaultValue);
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try {
            return new BigDecimal(get(key, defaultValue.toPlainString()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<SysConfig> listAll() {
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getConfigKey));
    }

    /** 更新配置值（后台专用，调用方负责权限与审计） */
    public void update(String key, String value, String remark) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "配置项不存在：" + key);
        }
        SysConfig update = new SysConfig();
        update.setId(config.getId());
        update.setConfigValue(value);
        if (StringUtils.hasText(remark)) {
            update.setRemark(remark);
        }
        sysConfigMapper.updateById(update);
        cache.put(key, value);
    }

    /** 免费抽成期：返回 true 表示当前仍处于免抽成期 */
    public boolean gratuityFreePeriod() {
        String until = get("gratuity.free.until", "");
        if (!StringUtils.hasText(until)) {
            return false;
        }
        try {
            return !LocalDate.now().isAfter(LocalDate.parse(until.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        listAll().forEach(c -> map.put(c.getConfigKey(), c.getConfigValue()));
        return map;
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_MILLIS && !cache.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (now - loadedAt < CACHE_MILLIS && !cache.isEmpty()) {
                return;
            }
            List<SysConfig> configs = listAll();
            cache.clear();
            configs.forEach(c -> cache.put(c.getConfigKey(), c.getConfigValue()));
            loadedAt = now;
        }
    }
}
