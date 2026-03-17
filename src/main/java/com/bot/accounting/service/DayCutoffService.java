package com.bot.accounting.service;

import com.bot.accounting.entity.SystemConfig;
import com.bot.accounting.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class DayCutoffService {

    private final SystemConfigMapper systemConfigMapper;

    public static final String DAY_CUTOFF_KEY = "day_cutoff_time";
    public static final String DEFAULT_CUTOFF_TIME = "00:00";
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 获取当前上海时间
     */
    public LocalDateTime now() {
        return LocalDateTime.now(SHANGHAI_ZONE);
    }

    /**
     * 将秒级时间戳转换为上海时区的 LocalDateTime
     */
    public LocalDateTime toLocalDateTime(Integer timestamp) {
        if (timestamp == null) return null;
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(timestamp), SHANGHAI_ZONE);
    }

    /**
     * 获取日切时间（默认 00:00）
     */
    public LocalTime getDayCutoffTime(Long chatId) {
        log.info("获取日切时间: chatId={}", chatId);
        
        SystemConfig config = systemConfigMapper.findByKeyAndChatId(DAY_CUTOFF_KEY, chatId);
        log.info("查询特定chatId配置: config={}", config != null ? config.getConfigValue() : "null");
        
        if (config == null) {
            config = systemConfigMapper.findGlobalByKey(DAY_CUTOFF_KEY);
            log.info("查询全局配置: config={}", config != null ? config.getConfigValue() : "null");
        }

        String timeStr = config != null ? config.getConfigValue() : DEFAULT_CUTOFF_TIME;
        log.info("最终使用的时间字符串: {}", timeStr);
        
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            LocalTime result = LocalTime.of(hour, minute);
            log.info("解析后的日切时间: {}", result);
            return result;
        } catch (Exception e) {
            log.warn("解析日切时间失败: {}, 使用默认值", timeStr);
            return LocalTime.of(0, 0);
        }
    }
    
    /**
     * 检查是否已设置日切时间（特定于该chatId）
     */
    public boolean isDayCutoffTimeSet(Long chatId) {
        SystemConfig config = systemConfigMapper.findByKeyAndChatId(DAY_CUTOFF_KEY, chatId);
        return config != null;
    }

    /**
     * 设置日切时间
     */
    public void setDayCutoffTime(Long chatId, int hour, int minute) {
        String timeStr = String.format("%02d:%02d", hour, minute);
        log.info("开始设置日切时间: chatId={}, timeStr={}", chatId, timeStr);

        // 先尝试查询特定chatId的配置
        SystemConfig config = systemConfigMapper.findByKeyAndChatId(DAY_CUTOFF_KEY, chatId);
        log.info("查询现有配置: config={}", config);
        
        if (config == null) {
            // 不存在则插入新记录
            log.info("配置不存在，插入新记录");
            config = new SystemConfig();
            config.setConfigKey(DAY_CUTOFF_KEY);
            config.setChatId(chatId);
            config.setDescription("日切时间，账目从该时间点开始记录新的一天");
            config.setConfigValue(timeStr);
            int result = systemConfigMapper.insert(config);
            log.info("插入结果: result={}, config.id={}", result, config.getId());
        } else {
            // 存在则更新
            log.info("配置已存在，更新记录: id={}, oldValue={}, newValue={}", 
                    config.getId(), config.getConfigValue(), timeStr);
            config.setConfigValue(timeStr);
            int result = systemConfigMapper.updateById(config);
            log.info("更新结果: result={}", result);
        }

        // 立即验证配置是否保存成功
        SystemConfig verifyConfig = systemConfigMapper.findByKeyAndChatId(DAY_CUTOFF_KEY, chatId);
        log.info("验证配置: savedValue={}", verifyConfig != null ? verifyConfig.getConfigValue() : "null");
        
        log.info("设置日切时间完成: chatId={}, time={}", chatId, timeStr);
    }

    /**
     * 获取当前账期日期（根据日切时间判断）
     * 如果当前时间 >= 日切时间，返回今天；否则返回昨天（昨天的账期还未结束）
     */
    public LocalDate getCurrentAccountingDate(Long chatId) {
        return getAccountingDate(chatId, now());
    }

    /**
     * 根据指定时间获取账期日期（根据日切时间判断）
     * 如果指定时间 >= 日切时间，返回当天；否则返回前一天（前一天的账期还未结束）
     */
    public LocalDate getAccountingDate(Long chatId, LocalDateTime dateTime) {
        log.info("计算会计日期: chatId={}, dateTime={}", chatId, dateTime);
        
        LocalTime cutoffTime = getDayCutoffTime(chatId);
        LocalDate date = dateTime.toLocalDate();
        LocalDateTime cutoffDateTime = LocalDateTime.of(date, cutoffTime);

        log.info("日切时间检查：date={}, cutoffTime={}, cutoffDateTime={}", date, cutoffTime, cutoffDateTime);
        log.info("比较结果：dateTime.isBefore(cutoffDateTime)={}", dateTime.isBefore(cutoffDateTime));

        if (dateTime.isBefore(cutoffDateTime)) {
            // 指定时间还没到日切时间，前一天的账期还未结束
            LocalDate result = date.minusDays(1);
            log.info("返回前一天（前一天账期未结束）: {}", result);
            return result;
        }
        log.info("返回当天: {}", date);
        return date;
    }

    /**
     * 获取今日开始时间（根据日切时间）
     * 返回当前账期的开始时间（日切时间）
     */
    public LocalDateTime getTodayStartTime(Long chatId) {
        LocalTime cutoffTime = getDayCutoffTime(chatId);
        LocalDate accountingDate = getCurrentAccountingDate(chatId);
        return LocalDateTime.of(accountingDate, cutoffTime);
    }

    /**
     * 获取今日结束时间（根据日切时间）
     * 返回当前账期的结束时间（次日日切时间减1纳秒）
     */
    public LocalDateTime getTodayEndTime(Long chatId) {
        return getTodayStartTime(chatId).plusDays(1).minusNanos(1);
    }

    /**
     * 获取指定月份的开始时间（考虑日切时间）
     */
    public LocalDateTime getMonthStartTime(Long chatId, YearMonth yearMonth) {
        LocalTime cutoffTime = getDayCutoffTime(chatId);
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        return LocalDateTime.of(firstDayOfMonth, cutoffTime);
    }

    /**
     * 获取指定月份的结束时间（考虑日切时间）
     */
    public LocalDateTime getMonthEndTime(Long chatId, YearMonth yearMonth) {
        LocalTime cutoffTime = getDayCutoffTime(chatId);
        LocalDate lastDayOfMonth = yearMonth.atEndOfMonth();
        // 下个月第一天的日切时间减 1 纳秒
        return LocalDateTime.of(lastDayOfMonth.plusDays(1), cutoffTime).minusNanos(1);
    }
}
