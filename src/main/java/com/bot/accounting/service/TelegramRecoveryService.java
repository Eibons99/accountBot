package com.bot.accounting.service;

import com.bot.accounting.bot.CommandDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramRecoveryService {

    private final TelegramHistoryService telegramHistoryService;
    private final ChatSyncStateService chatSyncStateService;
    private final CommandDispatcher commandDispatcher;
    private final TransactionService transactionService;

    // 最大回溯天数
    private static final int MAX_RECOVERY_DAYS = 7;

    /**
     * 应用启动时执行恢复
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用启动，开始从 Telegram 恢复未处理的账目...");
        
        // 获取所有需要同步的聊天
        List<Long> activeChatIds = getActiveChatIds();
        
        for (Long chatId : activeChatIds) {
            try {
                recoverChatMessages(chatId);
            } catch (Exception e) {
                log.error("恢复聊天消息失败: chatId={}", chatId, e);
            }
        }
        
        log.info("Telegram 消息恢复完成");
    }

    /**
     * 定时任务：每小时检查一次
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void scheduledRecovery() {
        log.debug("定时检查 Telegram 消息...");
        
        List<Long> activeChatIds = getActiveChatIds();
        
        for (Long chatId : activeChatIds) {
            try {
                // 检查上次同步时间，如果超过1小时未同步，执行恢复
                LocalDateTime lastSync = chatSyncStateService.getLastSyncTime(chatId);
                if (lastSync == null || ChronoUnit.HOURS.between(lastSync, LocalDateTime.now()) >= 1) {
                    recoverChatMessages(chatId);
                }
            } catch (Exception e) {
                log.error("定时恢复聊天消息失败: chatId={}", chatId, e);
            }
        }
    }

    /**
     * 恢复指定聊天的消息
     */
    public void recoverChatMessages(Long chatId) {
        log.info("开始恢复聊天消息: chatId={}", chatId);
        
        // 检查机器人是否还在群中
        if (!telegramHistoryService.isBotInChat(chatId)) {
            log.warn("机器人不在群中，跳过恢复: chatId={}", chatId);
            return;
        }
        
        // 获取最后处理的消息ID
        Integer lastProcessedId = chatSyncStateService.getLastProcessedMessageId(chatId);
        
        // 从 Telegram 获取历史消息
        List<Message> messages = telegramHistoryService.getChatHistory(chatId, lastProcessedId);
        
        if (messages.isEmpty()) {
            log.debug("没有需要恢复的消息：chatId={}, lastProcessedId={}", chatId, lastProcessedId);
            return;
        }
                
        log.info("获取到 {} 条待处理消息：chatId={}, lastProcessedId={}", messages.size(), chatId, lastProcessedId);
        
        int successCount = 0;
        int failCount = 0;
        Integer maxMessageId = lastProcessedId;
        
        for (Message message : messages) {
            try {
                // 检查消息是否已处理（通过数据库幂等性检查）
                if (isMessageAlreadyProcessed(chatId, message.getMessageId())) {
                    log.debug("消息已处理过，跳过：chatId={}, messageId={}, text={}", 
                            chatId, message.getMessageId(), truncateText(message.getText()));
                    maxMessageId = Math.max(maxMessageId != null ? maxMessageId : 0, message.getMessageId());
                    continue;
                }
                
                // 处理消息
                Object responseObj = commandDispatcher.dispatch(message);
                
                // 只处理 String 类型的响应
                if (responseObj instanceof String) {
                    String response = (String) responseObj;
                    if (response != null && !response.isEmpty() && !response.contains("失败") && !response.contains("错误")) {
                        successCount++;
                        log.info("成功恢复消息：chatId={}, messageId={}, fromUser={}, text={}", 
                                chatId, message.getMessageId(), 
                                message.getFrom() != null ? message.getFrom().getUserName() : "unknown",
                                truncateText(message.getText()));
                    } else {
                        failCount++;
                        log.warn("恢复消息失败：chatId={}, messageId={}, text={}, response={}", 
                                chatId, message.getMessageId(), truncateText(message.getText()), response);
                    }
                } else if (responseObj != null) {
                    // SendMessage 或其他类型，认为成功（因为已经发送了）
                    successCount++;
                    log.info("成功恢复消息（带键盘）：chatId={}, messageId={}", chatId, message.getMessageId());
                }
                
                // 更新最大消息ID
                maxMessageId = Math.max(maxMessageId != null ? maxMessageId : 0, message.getMessageId());
                
            } catch (Exception e) {
                failCount++;
                log.error("处理消息时发生错误：chatId={}, messageId={}, text={}", 
                        chatId, message.getMessageId(), truncateText(message.getText()), e);
            }
        }
        
        // 更新最后处理的消息ID
        if (maxMessageId != null && (lastProcessedId == null || maxMessageId > lastProcessedId)) {
            chatSyncStateService.updateLastProcessedMessageId(chatId, maxMessageId);
        }
        
        log.info("聊天消息恢复完成：chatId={}, 成功={}, 失败={}", chatId, successCount, failCount);
    }
        
    /**
     * 截断文本用于日志显示（最多 50 个字符）
     */
    private String truncateText(String text) {
        if (text == null || text.isEmpty()) {
            return "(无文本)";
        }
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }

    /**
     * 检查消息是否已处理过
     * 通过查询数据库中是否存在该消息对应的交易记录
     */
    private boolean isMessageAlreadyProcessed(Long chatId, Integer messageId) {
        // 这里可以通过查询 transactions 表来检查
        // 简化处理：假设如果消息ID小于等于最后处理的消息ID，则认为已处理
        Integer lastProcessedId = chatSyncStateService.getLastProcessedMessageId(chatId);
        return lastProcessedId != null && messageId <= lastProcessedId;
    }

    /**
     * 获取所有活跃的聊天ID
     * 从数据库中获取有记录的聊天ID
     */
    private List<Long> getActiveChatIds() {
        // 从 chat_sync_state 表获取所有聊天ID
        return chatSyncStateService.list().stream()
                .map(com.bot.accounting.entity.ChatSyncState::getChatId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 手动触发全量同步
     */
    public void triggerFullSync(Long chatId) {
        log.info("触发全量同步: chatId={}", chatId);
        chatSyncStateService.markForFullSync(chatId);
        recoverChatMessages(chatId);
    }
}
