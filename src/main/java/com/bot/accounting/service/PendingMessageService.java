package com.bot.accounting.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingMessageService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key 前缀
    private static final String PENDING_TAG_KEY_PREFIX = "pending:tag:";
    private static final String PENDING_OPERATOR_KEY_PREFIX = "pending:operator:";
    private static final String PROCESSING_LOCK_PREFIX = "processing:";

    // 消息过期时间：7天
    private static final long MESSAGE_EXPIRE_DAYS = 7;

    /**
     * 缓存标记员发送的消息，等待操作员回复
     */
    public void cacheTaggedMessage(Long chatId, Integer messageId, Long taggedUserId, 
                                    String messageText, String taggedUserName) {
        String key = PENDING_TAG_KEY_PREFIX + chatId + ":" + messageId;
        
        PendingTagMessage pendingMessage = new PendingTagMessage();
        pendingMessage.setChatId(chatId);
        pendingMessage.setMessageId(messageId);
        pendingMessage.setTaggedUserId(taggedUserId);
        pendingMessage.setTaggedUserName(taggedUserName);
        pendingMessage.setMessageText(messageText);
        pendingMessage.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));

        redisTemplate.opsForValue().set(key, pendingMessage, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.info("缓存标记消息: chatId={}, messageId={}, user={}", chatId, messageId, taggedUserName);
    }

    /**
     * 获取并移除待处理的标记消息
     */
    public PendingTagMessage getAndRemoveTaggedMessage(Long chatId, Integer messageId) {
        String key = PENDING_TAG_KEY_PREFIX + chatId + ":" + messageId;
        
        PendingTagMessage message = (PendingTagMessage) redisTemplate.opsForValue().get(key);
        if (message != null) {
            redisTemplate.delete(key);
            log.info("获取并移除标记消息: chatId={}, messageId={}", chatId, messageId);
        }
        return message;
    }

    /**
     * 缓存操作员发送的待处理消息（当数据库不可用时）
     */
    public void cacheOperatorMessage(Long chatId, Integer messageId, Long operatorUserId,
                                      String messageText, String operatorUserName, 
                                      Integer replyToMessageId) {
        String key = PENDING_OPERATOR_KEY_PREFIX + chatId + ":" + messageId;
        
        PendingOperatorMessage pendingMessage = new PendingOperatorMessage();
        pendingMessage.setChatId(chatId);
        pendingMessage.setMessageId(messageId);
        pendingMessage.setOperatorUserId(operatorUserId);
        pendingMessage.setOperatorUserName(operatorUserName);
        pendingMessage.setMessageText(messageText);
        pendingMessage.setReplyToMessageId(replyToMessageId);
        pendingMessage.setCreatedAt(LocalDateTime.now());
        pendingMessage.setRetryCount(0);

        redisTemplate.opsForValue().set(key, pendingMessage, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.info("缓存操作员消息: chatId={}, messageId={}, user={}", chatId, messageId, operatorUserName);
    }

    /**
     * 获取所有待处理的操作员消息
     */
    public List<PendingOperatorMessage> getAllPendingOperatorMessages(Long chatId) {
        String pattern = PENDING_OPERATOR_KEY_PREFIX + chatId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        List<PendingOperatorMessage> messages = new ArrayList<>();
        for (String key : keys) {
            PendingOperatorMessage message = (PendingOperatorMessage) redisTemplate.opsForValue().get(key);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * 获取所有待处理的标记消息
     */
    public List<PendingTagMessage> getAllPendingTaggedMessages(Long chatId) {
        String pattern = PENDING_TAG_KEY_PREFIX + chatId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        List<PendingTagMessage> messages = new ArrayList<>();
        for (String key : keys) {
            PendingTagMessage message = (PendingTagMessage) redisTemplate.opsForValue().get(key);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * 移除已处理的消息
     */
    public void removePendingOperatorMessage(Long chatId, Integer messageId) {
        String key = PENDING_OPERATOR_KEY_PREFIX + chatId + ":" + messageId;
        redisTemplate.delete(key);
        log.info("移除已处理消息: chatId={}, messageId={}", chatId, messageId);
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount(Long chatId, Integer messageId) {
        String key = PENDING_OPERATOR_KEY_PREFIX + chatId + ":" + messageId;
        PendingOperatorMessage message = (PendingOperatorMessage) redisTemplate.opsForValue().get(key);
        if (message != null) {
            message.setRetryCount(message.getRetryCount() + 1);
            message.setLastRetryAt(LocalDateTime.now());
            redisTemplate.opsForValue().set(key, message, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * 获取所有需要恢复的聊天ID列表
     */
    public Set<String> getAllChatIdsWithPendingMessages() {
        Set<String> tagKeys = redisTemplate.keys(PENDING_TAG_KEY_PREFIX + "*");
        Set<String> operatorKeys = redisTemplate.keys(PENDING_OPERATOR_KEY_PREFIX + "*");
        
        Set<String> allKeys = new java.util.HashSet<>();
        if (tagKeys != null) {
            allKeys.addAll(tagKeys);
        }
        if (operatorKeys != null) {
            allKeys.addAll(operatorKeys);
        }
        return allKeys;
    }

    /**
     * 锁定消息处理（防止并发处理）
     */
    public boolean tryLock(Long chatId, Integer messageId) {
        String key = PROCESSING_LOCK_PREFIX + chatId + ":" + messageId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "1", 30, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放锁
     */
    public void unlock(Long chatId, Integer messageId) {
        String key = PROCESSING_LOCK_PREFIX + chatId + ":" + messageId;
        redisTemplate.delete(key);
    }

    /**
     * 获取 RedisTemplate（供外部使用）
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    // ========== 数据类 ==========

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PendingTagMessage implements Serializable {
        private Long chatId;
        private Integer messageId;
        private Long taggedUserId;
        private String taggedUserName;
        private String messageText;
        private LocalDateTime createdAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PendingOperatorMessage implements Serializable {
        private Long chatId;
        private Integer messageId;
        private Long operatorUserId;
        private String operatorUserName;
        private String messageText;
        private Integer replyToMessageId;
        private LocalDateTime createdAt;
        private Integer retryCount;
        private LocalDateTime lastRetryAt;
    }
}
