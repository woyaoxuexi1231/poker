package com.poker.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.authentication.rememberme.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 自定义 Remember-Me Token Repository
 * 适配 poker_persistent_logins 表
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomPersistentTokenRepository implements PersistentTokenRepository {
    
    private final PersistentLoginMapper persistentLoginMapper;
    
    @Override
    public void createNewToken(PersistentRememberMeToken token) {
        try {
            PersistentLogin login = new PersistentLogin();
            login.setUsername(token.getUsername());
            login.setSeries(token.getSeries());
            login.setToken(token.getTokenValue());
            login.setLastUsed(token.getDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            
            persistentLoginMapper.insertPersistentLogin(login);
            log.info("✅ 创建 Remember-Me Token: series={}, username={}", token.getSeries(), token.getUsername());
        } catch (Exception e) {
            log.error("🚫 创建 Remember-Me Token 失败: {}", token.getSeries(), e);
            throw new RuntimeException("创建 Remember-Me Token 失败", e);
        }
    }
    
    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        try {
            LocalDateTime localDateTime = lastUsed.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            persistentLoginMapper.updateTokenBySeries(series, tokenValue, localDateTime);
            log.debug("🔄 更新 Remember-Me Token: series={}", series);
        } catch (Exception e) {
            log.error("🚫 更新 Remember-Me Token 失败: {}", series, e);
            throw new RuntimeException("更新 Remember-Me Token 失败", e);
        }
    }
    
    @Override
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        try {
            PersistentLogin login = persistentLoginMapper.selectBySeries(seriesId);
            if (login == null) {
                log.warn("⚠️ 未找到 Remember-Me Token: series={}", seriesId);
                return null;
            }
            
            Date lastUsed = Date.from(login.getLastUsed().atZone(java.time.ZoneId.systemDefault()).toInstant());
            return new PersistentRememberMeToken(
                login.getUsername(),
                login.getSeries(),
                login.getToken(),
                lastUsed
            );
        } catch (Exception e) {
            log.error("🚫 查询 Remember-Me Token 失败: {}", seriesId, e);
            return null;
        }
    }
    
    @Override
    public void removeUserTokens(String username) {
        try {
            persistentLoginMapper.deleteByUsername(username);
            log.info("🗑️ 删除用户所有 Remember-Me Tokens: username={}", username);
        } catch (Exception e) {
            log.error("🚫 删除 Remember-Me Tokens 失败: username={}", username, e);
        }
    }
}
