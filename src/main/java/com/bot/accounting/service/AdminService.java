package com.bot.accounting.service;

import com.bot.accounting.config.BotConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final BotConfig botConfig;
    
    /**
     * 检查用户是否是管理员
     */
    public boolean isAdmin(Long telegramId) {
        List<Long> adminIds = botConfig.getAdminIds();
        if (adminIds == null || adminIds.isEmpty()) {
            return false;
        }
        return adminIds.contains(telegramId);
    }
    
    /**
     * 检查消息发送者是否是管理员
     */
    public boolean isAdmin(Message message) {
        if (message == null || message.getFrom() == null) {
            return false;
        }
        return isAdmin(message.getFrom().getId());
    }
    
    /**
     * 验证管理员权限，如果不是管理员则抛出异常
     */
    public void requireAdmin(Message message) {
        if (!isAdmin(message)) {
            throw new RuntimeException("权限不足，只有管理员才能执行此操作");
        }
    }
    
    /**
     * 获取管理员列表
     */
    public List<Long> getAdminIds() {
        return botConfig.getAdminIds();
    }
}
