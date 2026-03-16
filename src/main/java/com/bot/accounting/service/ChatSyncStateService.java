package com.bot.accounting.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bot.accounting.entity.ChatSyncState;
import com.bot.accounting.mapper.ChatSyncStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatSyncStateService extends ServiceImpl<ChatSyncStateMapper, ChatSyncState> {

    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis 缓存前缀
    private static final String SYNC_STATE_KEY_PREFIX = "sync:state:";

    public ChatSyncStateService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取聊天最后处理的消息ID
     */
    public Integer getLastProcessedMessageId(Long chatId) {
        // 先查 Redis 缓存
        String redisKey = SYNC_STATE_KEY_PREFIX + chatId;
        Integer cached = (Integer) redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return cached;
        }

        // 再查数据库
        ChatSyncState state = getOne(new QueryWrapper<ChatSyncState>().eq("chat_id", chatId));
        if (state != null) {
            // 缓存到 Redis
            redisTemplate.opsForValue().set(redisKey, state.getLastMessageId(), 1, TimeUnit.HOURS);
            return state.getLastMessageId();
        }

        return null;
    }

    /**
     * 更新最后处理的消息ID
     */
    public void updateLastProcessedMessageId(Long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }

        // 更新 Redis
        String redisKey = SYNC_STATE_KEY_PREFIX + chatId;
        redisTemplate.opsForValue().set(redisKey, messageId, 1, TimeUnit.HOURS);

        // 更新数据库
        ChatSyncState state = getOne(new QueryWrapper<ChatSyncState>().eq("chat_id", chatId));
        if (state == null) {
            state = new ChatSyncState();
            state.setChatId(chatId);
            state.setLastMessageId(messageId);
            state.setLastSyncTime(LocalDateTime.now());
            save(state);
        } else {
            // 只更新更大的 messageId
            if (state.getLastMessageId() == null || messageId > state.getLastMessageId()) {
                state.setLastMessageId(messageId);
                state.setLastSyncTime(LocalDateTime.now());
                updateById(state);
            }
        }

        log.debug("更新最后处理消息ID: chatId={}, messageId={}", chatId, messageId);
    }

    /**
     * 获取最后同步时间
     */
    public LocalDateTime getLastSyncTime(Long chatId) {
        ChatSyncState state = getOne(new QueryWrapper<ChatSyncState>().eq("chat_id", chatId));
        return state != null ? state.getLastSyncTime() : null;
    }

    /**
     * 标记需要全量同步
     */
    public void markForFullSync(Long chatId) {
        ChatSyncState state = getOne(new QueryWrapper<ChatSyncState>().eq("chat_id", chatId));
        if (state == null) {
            state = new ChatSyncState();
            state.setChatId(chatId);
            state.setNeedFullSync(true);
            state.setLastSyncTime(LocalDateTime.now());
            save(state);
        } else {
            state.setNeedFullSync(true);
            updateById(state);
        }

        // 清除 Redis 缓存，强制从数据库重新加载
        String redisKey = SYNC_STATE_KEY_PREFIX + chatId;
        redisTemplate.delete(redisKey);

        log.info("标记聊天需要全量同步: chatId={}", chatId);
    }

    /**
     * 检查是否需要全量同步
     */
    public boolean needFullSync(Long chatId) {
        ChatSyncState state = getOne(new QueryWrapper<ChatSyncState>().eq("chat_id", chatId));
        return state != null && Boolean.TRUE.equals(state.getNeedFullSync());
    }

    /**
     * 完成全量同步
     */
    public void completeFullSync(Long chatId, Integer lastMessageId) {
        ChatSyncState state = getOne(new QueryWrapper<ChatSyncState>().eq("chat_id", chatId));
        if (state != null) {
            state.setNeedFullSync(false);
            state.setLastMessageId(lastMessageId);
            state.setLastSyncTime(LocalDateTime.now());
            updateById(state);

            // 更新 Redis
            String redisKey = SYNC_STATE_KEY_PREFIX + chatId;
            redisTemplate.opsForValue().set(redisKey, lastMessageId, 1, TimeUnit.HOURS);
        }

        log.info("完成全量同步: chatId={}, lastMessageId={}", chatId, lastMessageId);
    }
}
