package com.bot.accounting.service;

import com.bot.accounting.entity.SystemConfig;
import com.bot.accounting.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DayCutoffService {
    
    private final SystemConfigMapper systemConfigMapper;
    
    public static final String DAY_CUTOFF_KEY = "day_cutoff_time";
    public static final String DEFAULT_CUTOFF_TIME = "00:00";
    
    /**
     * 获取日切时间（默认 00:00）
     */
    public LocalTime getDayCutoffTime(Long chatId) {
        SystemConfig config = systemConfigMapper.findByKeyAndChatId(DAY_CUTOFF_KEY, chatId);
        if (config == null) {
            config = systemConfigMapper.findGlobalByKey(DAY_CUTOFF_KEY);
        }
        
        String timeStr = config != null ? config.getConfigValue() : DEFAULT_CUTOFF_TIME;
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return LocalTime.of(hour, minute);
        } catch (Exception e) {
            log.warn("解析日切时间失败: {}, 使用默认值", timeStr);
            return LocalTime.of(0, 0);
        }
    }
    
    /**
     * 设置日切时间
     */
    public void setDayCutoffTime(Long chatId, int hour, int minute) {
        String timeStr = String.format("%02d:%02d", hour, minute);
        
        SystemConfig config = systemConfigMapper.findByKeyAndChatId(DAY_CUTOFF_KEY, chatId);
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(DAY_CUTOFF_KEY);
            config.setChatId(chatId);
            config.setDescription("日切时间，账目从该时间点开始记录新的一天");
            config.setConfigValue(timeStr);
            systemConfigMapper.insert(config);
        } else {
            config.setConfigValue(timeStr);
            systemConfigMapper.updateById(config);
        }
        
        log.info("设置日切时间: chatId={}, time={}", chatId, timeStr);
    }
    
    /**
     * 获取当前账期日期（根据日切时间判断）
     * 如果当前时间 >= 日切时间，返回今天；否则返回昨天
     */
    public LocalDate getCurrentAccountingDate(Long chatId) {
        LocalTime cutoffTime = getDayCutoffTime(chatId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffDateTime = LocalDateTime.of(LocalDate.now(), cutoffTime);
        
        if (now.isBefore(cutoffDateTime)) {
            // 当前时间还没到日切时间，算昨天
            return LocalDate.now().minusDays(1);
        }
        return LocalDate.now();
    }
    
    /**
     * 获取今日开始时间（根据日切时间）
     */
    public LocalDateTime getTodayStartTime(Long chatId) {
        LocalTime cutoffTime = getDayCutoffTime(chatId);
        LocalDate accountingDate = getCurrentAccountingDate(chatId);
        return LocalDateTime.of(accountingDate, cutoffTime);
    }
    
    /**
     * 获取今日结束时间（根据日切时间）
     */
    public LocalDateTime getTodayEndTime(Long chatId) {
        return getTodayStartTime(chatId).plusDays(1).minusSeconds(1);
    }
}
