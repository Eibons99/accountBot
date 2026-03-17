package com.bot.accounting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPermissionService {
    
    private final ObjectProvider<TelegramLongPollingBot> botProvider;
    private final AdminService adminService;
    
    // 缓存群主信息，避免频繁调用API (chatId_userId -> isCreator)
    private final ConcurrentHashMap<String, Boolean> creatorCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间：5分钟
    private static final long CACHE_EXPIRE_MS = 5 * 60 * 1000;
    
    /**
     * 检查用户是否是群主（创建者）
     * 通过 Telegram API 查询
     */
    public boolean isGroupCreator(Message message) {
        // 私聊没有群主概念
        if (message.getChat().isUserChat()) {
            return false;
        }
        
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        String cacheKey = chatId + "_" + userId;
        
        // 检查缓存
        Boolean cached = creatorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            TelegramLongPollingBot bot = botProvider.getIfAvailable();
            if (bot == null) {
                log.warn("Bot 尚未初始化，无法查询权限");
                return false;
            }
            
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId.toString());
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = bot.execute(getChatMember);
            
            // 检查是否是创建者
            boolean isCreator = "creator".equals(chatMember.getStatus());
            
            // 缓存结果
            creatorCache.put(cacheKey, isCreator);
            
            // 5分钟后清除缓存
            new Thread(() -> {
                try {
                    Thread.sleep(CACHE_EXPIRE_MS);
                    creatorCache.remove(cacheKey);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return isCreator;
        } catch (TelegramApiException e) {
            log.error("查询用户权限失败: chatId={}, userId={}, error={}", chatId, userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查用户是否是管理员（包括群主）
     */
    public boolean isGroupAdmin(Message message) {
        // 私聊没有管理员概念
        if (message.getChat().isUserChat()) {
            return false;
        }
        
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        String cacheKey = chatId + "_" + userId + "_admin";
        
        // 检查缓存
        Boolean cached = creatorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            TelegramLongPollingBot bot = botProvider.getIfAvailable();
            if (bot == null) {
                log.warn("Bot 尚未初始化，无法查询权限");
                return false;
            }
            
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId.toString());
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = bot.execute(getChatMember);
            
            // 检查是否是创建者或管理员
            String status = chatMember.getStatus();
            boolean isAdmin = "creator".equals(status) || "administrator".equals(status);
            
            // 缓存结果
            creatorCache.put(cacheKey, isAdmin);
            
            // 5分钟后清除缓存
            new Thread(() -> {
                try {
                    Thread.sleep(CACHE_EXPIRE_MS);
                    creatorCache.remove(cacheKey);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return isAdmin;
        } catch (TelegramApiException e) {
            log.error("查询用户权限失败: chatId={}, userId={}, error={}", chatId, userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 清除缓存（用于测试或权限变更时）
     */
    public void clearCache(Long chatId, Long userId) {
        creatorCache.remove(chatId + "_" + userId);
        creatorCache.remove(chatId + "_" + userId + "_admin");
    }
    
    /**
     * 检查指定用户是否是群管理员（用于验证标记员/操作员设置的前提条件）
     * Telegram 限制：机器人只能识别和操作群管理员
     */
    public boolean isUserGroupAdmin(Long chatId, Long userId) {
        String cacheKey = chatId + "_" + userId + "_check";
        
        // 检查缓存
        Boolean cached = creatorCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            TelegramLongPollingBot bot = botProvider.getIfAvailable();
            if (bot == null) {
                log.warn("Bot 尚未初始化，无法查询权限");
                return false;
            }
            
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId.toString());
            getChatMember.setUserId(userId);
            
            ChatMember chatMember = bot.execute(getChatMember);
            
            // 检查是否是创建者或管理员
            String status = chatMember.getStatus();
            boolean isAdmin = "creator".equals(status) || "administrator".equals(status);
            
            // 缓存结果
            creatorCache.put(cacheKey, isAdmin);
            
            // 5分钟后清除缓存
            new Thread(() -> {
                try {
                    Thread.sleep(CACHE_EXPIRE_MS);
                    creatorCache.remove(cacheKey);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return isAdmin;
        } catch (TelegramApiException e) {
            log.error("查询用户权限失败: chatId={}, userId={}, error={}", chatId, userId, e.getMessage());
            // 如果无法确认，默认返回 false（安全起见）
            return false;
        }
    }
}
