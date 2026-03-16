package com.bot.accounting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRecoveryService {

    private final PendingMessageService pendingMessageService;
    private final TagService tagService;
    private final TransactionService transactionService;
    private final UserService userService;
    private final DayCutoffService dayCutoffService;

    // 匹配 +金额 格式
    private static final Pattern PLUS_PATTERN = Pattern.compile("^\\+(\\d+(?:\\.\\d{1,2})?)$");
    // 匹配 -金额 格式
    private static final Pattern MINUS_PATTERN = Pattern.compile("^-(\\d+(?:\\.\\d{1,2})?)$");

    /**
     * 应用启动时执行恢复
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用启动，开始检查待恢复的消息...");
        recoverPendingMessages();
    }

    /**
     * 定时任务：每5分钟检查一次待处理消息
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scheduledRecovery() {
        log.debug("定时检查待处理消息...");
        recoverPendingMessages();
    }

    /**
     * 恢复所有待处理的消息
     */
    @Transactional
    public void recoverPendingMessages() {
        try {
            // 获取所有有待处理消息的聊天ID
            java.util.Set<String> allKeys = pendingMessageService.getAllChatIdsWithPendingMessages();
            
            if (allKeys.isEmpty()) {
                log.debug("没有待处理的消息");
                return;
            }

            log.info("发现 {} 条待处理消息，开始恢复...", allKeys.size());

            // 提取唯一的 chatId
            for (String key : allKeys) {
                try {
                    // 解析 chatId 从 key (格式: pending:tag:{chatId}:{messageId} 或 pending:operator:{chatId}:{messageId})
                    String[] parts = key.split(":");
                    if (parts.length >= 3) {
                        Long chatId = Long.parseLong(parts[2]);
                        processPendingMessagesForChat(chatId);
                    }
                } catch (Exception e) {
                    log.error("处理待恢复消息失败: key={}, error={}", key, e.getMessage());
                }
            }

            log.info("消息恢复完成");

        } catch (Exception e) {
            log.error("恢复待处理消息时发生错误", e);
        }
    }

    /**
     * 处理指定聊天的待处理消息
     */
    private void processPendingMessagesForChat(Long chatId) {
        // 1. 处理待处理的标记消息（等待操作员回复的）
        List<PendingMessageService.PendingTagMessage> pendingTags = 
                pendingMessageService.getAllPendingTaggedMessages(chatId);
        
        for (PendingMessageService.PendingTagMessage tagMsg : pendingTags) {
            log.info("发现待处理标记消息: chatId={}, messageId={}, text={}", 
                    chatId, tagMsg.getMessageId(), tagMsg.getMessageText());
            // 标记消息不需要立即处理，等待操作员回复
            // 这里可以添加超时逻辑
        }

        // 2. 处理待处理的操作员消息（数据库失败时缓存的）
        List<PendingMessageService.PendingOperatorMessage> pendingOps = 
                pendingMessageService.getAllPendingOperatorMessages(chatId);
        
        for (PendingMessageService.PendingOperatorMessage opMsg : pendingOps) {
            try {
                processPendingOperatorMessage(opMsg);
            } catch (Exception e) {
                log.error("处理操作员消息失败: chatId={}, messageId={}, error={}", 
                        chatId, opMsg.getMessageId(), e.getMessage());
                
                // 增加重试次数
                pendingMessageService.incrementRetryCount(chatId, opMsg.getMessageId());
                
                // 如果重试次数超过5次，发送告警或通知
                if (opMsg.getRetryCount() != null && opMsg.getRetryCount() >= 5) {
                    log.error("消息处理失败超过5次，需要人工介入: chatId={}, messageId={}", 
                            chatId, opMsg.getMessageId());
                }
            }
        }
    }

    /**
     * 处理单个待处理的操作员消息
     */
    private void processPendingOperatorMessage(PendingMessageService.PendingOperatorMessage opMsg) {
        Long chatId = opMsg.getChatId();
        Integer messageId = opMsg.getMessageId();

        // 尝试获取锁，防止并发处理
        if (!pendingMessageService.tryLock(chatId, messageId)) {
            log.debug("消息正在被其他线程处理，跳过: chatId={}, messageId={}", chatId, messageId);
            return;
        }
        
        // 幂等性检查：检查是否已处理过（通过 message_id 和 chat_id）
        if (isAlreadyProcessed(chatId, messageId)) {
            log.info("消息已处理过，跳过: chatId={}, messageId={}", chatId, messageId);
            pendingMessageService.removePendingOperatorMessage(chatId, messageId);
            return;
        }

        try {
            String text = opMsg.getMessageText();
            Long operatorUserId = opMsg.getOperatorUserId();
            Integer replyToMessageId = opMsg.getReplyToMessageId();

            // 解析金额
            BigDecimal amount = null;
            boolean isIncome = true;

            Matcher plusMatcher = PLUS_PATTERN.matcher(text.trim());
            if (plusMatcher.matches()) {
                amount = new BigDecimal(plusMatcher.group(1));
                isIncome = true;
            } else {
                Matcher minusMatcher = MINUS_PATTERN.matcher(text.trim());
                if (minusMatcher.matches()) {
                    amount = new BigDecimal(minusMatcher.group(1));
                    isIncome = false;
                }
            }

            if (amount == null) {
                log.warn("无法解析金额，移除消息: chatId={}, messageId={}", chatId, messageId);
                pendingMessageService.removePendingOperatorMessage(chatId, messageId);
                return;
            }

            // 获取或创建操作员用户
            com.bot.accounting.entity.User operatorUser = userService.findByTelegramId(operatorUserId);
            if (operatorUser == null) {
                operatorUser = userService.getOrCreateUser(operatorUserId, opMsg.getOperatorUserName(), null, null);
            }

            // 如果是回复标记消息
            if (replyToMessageId != null) {
                PendingMessageService.PendingTagMessage tagMsg = 
                        pendingMessageService.getAndRemoveTaggedMessage(chatId, replyToMessageId);
                
                if (tagMsg != null) {
                    // 获取或创建标记员用户
                    com.bot.accounting.entity.User taggedUser = userService.findByTelegramId(tagMsg.getTaggedUserId());
                    if (taggedUser == null) {
                        taggedUser = userService.getOrCreateUser(
                                tagMsg.getTaggedUserId(), tagMsg.getTaggedUserName(), null, null);
                    }

                    // 记录标记记录
                    tagService.recordTaggedTransaction(
                            tagMsg.getTaggedUserId(),
                            operatorUserId,
                            amount,
                            tagMsg.getMessageText(),
                            chatId
                    );

                    // 记录交易
                    transactionService.addTransaction(
                            operatorUser,
                            amount,
                            isIncome ? com.bot.accounting.entity.Transaction.TransactionType.INCOME 
                                    : com.bot.accounting.entity.Transaction.TransactionType.EXPENSE,
                            isIncome ? "标记入账" : "标记出账",
                            String.format("标记员:%s 操作员:%s", 
                                    tagMsg.getTaggedUserName(), opMsg.getOperatorUserName()),
                            chatId,
                            taggedUser.getId(),
                            operatorUser.getId()
                    );

                    log.info("成功恢复标记交易: chatId={}, amount={}, taggedUser={}, operator={}",
                            chatId, amount, tagMsg.getTaggedUserName(), opMsg.getOperatorUserName());
                } else {
                    // 标记消息已过期或不存在，直接记录操作员交易
                    recordDirectOperatorTransaction(opMsg, amount, isIncome);
                }
            } else {
                // 直接记录操作员交易
                recordDirectOperatorTransaction(opMsg, amount, isIncome);
            }

            // 移除已处理的消息
            pendingMessageService.removePendingOperatorMessage(chatId, messageId);
            
            // 标记为已处理（幂等性）
            markAsProcessed(chatId, messageId);
            
            log.info("成功恢复操作员消息: chatId={}, messageId={}", chatId, messageId);

        } finally {
            pendingMessageService.unlock(chatId, messageId);
        }
    }

    /**
     * 直接记录操作员交易（无标记员）
     */
    private void recordDirectOperatorTransaction(PendingMessageService.PendingOperatorMessage opMsg, 
                                                  BigDecimal amount, boolean isIncome) {
        com.bot.accounting.entity.User operatorUser = userService.findByTelegramId(opMsg.getOperatorUserId());
        if (operatorUser == null) {
            operatorUser = userService.getOrCreateUser(
                    opMsg.getOperatorUserId(), opMsg.getOperatorUserName(), null, null);
        }

        transactionService.addTransaction(
                operatorUser,
                amount,
                isIncome ? com.bot.accounting.entity.Transaction.TransactionType.INCOME 
                        : com.bot.accounting.entity.Transaction.TransactionType.EXPENSE,
                isIncome ? "直接入账" : "直接出账",
                String.format("操作员:%s（无标记员）", opMsg.getOperatorUserName()),
                opMsg.getChatId(),
                null,
                operatorUser.getId()
        );

        log.info("成功恢复直接交易: chatId={}, amount={}, operator={}",
                opMsg.getChatId(), amount, opMsg.getOperatorUserName());
    }
    
    /**
     * 检查消息是否已处理过（幂等性检查）
     * 通过查询数据库中是否存在相同 chat_id 和 message_id 的记录
     */
    private boolean isAlreadyProcessed(Long chatId, Integer messageId) {
        try {
            // 查询数据库中是否存在该消息对应的交易记录
            // 这里简化处理，实际应该查询 transactions 表
            // 通过 message_id 关联（需要在 transactions 表添加 message_id 字段）
            
            // 临时方案：检查 Redis 中是否有处理完成标记
            String processedKey = "processed:" + chatId + ":" + messageId;
            Boolean processed = (Boolean) pendingMessageService.getRedisTemplate().opsForValue().get(processedKey);
            return Boolean.TRUE.equals(processed);
        } catch (Exception e) {
            log.error("幂等性检查失败", e);
            return false;
        }
    }
    
    /**
     * 标记消息已处理（用于幂等性）
     */
    private void markAsProcessed(Long chatId, Integer messageId) {
        try {
            String processedKey = "processed:" + chatId + ":" + messageId;
            pendingMessageService.getRedisTemplate().opsForValue().set(processedKey, true, 1, java.util.concurrent.TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("标记消息已处理失败", e);
        }
    }
}
