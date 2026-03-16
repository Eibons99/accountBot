package com.bot.accounting.dto;

import com.bot.accounting.entity.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class TransactionDTO {
    private Long id;
    private BigDecimal amount;
    private Transaction.TransactionType type;
    private String category;
    private String description;
    private LocalDate transactionDate;
    private LocalDateTime createdAt;
    private String userName;
    private String operatorName;  // 操作员姓名
    private String taggedUserName;  // 标记员姓名
    private Integer telegramMessageId;  // Telegram 消息 ID
    private Long chatId;  // 聊天 ID，用于生成消息链接
    
    public String getFormattedAmount() {
        String symbol = type == Transaction.TransactionType.INCOME ? "+" : "-";
        return symbol + "¥" + amount.toString();
    }
    
    public String getFormattedDate() {
        return transactionDate.format(DateTimeFormatter.ofPattern("MM-dd"));
    }
    
    public String getTypeEmoji() {
        return type == Transaction.TransactionType.INCOME ? "💰" : "💸";
    }
    
    /**
     * 生成 Telegram 消息链接
     * 格式：https://t.me/c/{chat_id}/{message_id}
     */
    public String getMessageLink() {
        if (telegramMessageId == null || chatId == null) {
            return null;
        }
        // 对于私有群组/频道，需要使用 -100 前缀
        String formattedChatId = chatId.toString();
        if (!formattedChatId.startsWith("-100")) {
            formattedChatId = "-100" + formattedChatId;
        }
        return String.format("https://t.me/c/%s/%d", 
                formattedChatId.substring(4),  // 移除 -100 前缀
                telegramMessageId);
    }
}
