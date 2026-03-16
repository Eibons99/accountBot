package com.bot.accounting.bot.command;

import com.bot.accounting.entity.Transaction;
import com.bot.accounting.entity.User;
import com.bot.accounting.service.DayCutoffService;
import com.bot.accounting.service.MessageParser;
import com.bot.accounting.service.TransactionService;
import com.bot.accounting.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AddCommand implements BotCommand {
    
    private final TransactionService transactionService;
    private final UserService userService;
    private final MessageParser messageParser;
    private final DayCutoffService dayCutoffService;
    
    @Override
    public String execute(Message message) {
        User user = userService.getOrCreateUser(message);
        MessageParser.ParseResult result = messageParser.parse(message.getText());
        
        if (!result.isSuccess()) {
            return "❌ " + result.getErrorMessage();
        }
        
        // 强制设置为支出
        Transaction.TransactionType type = Transaction.TransactionType.EXPENSE;
        
        transactionService.addTransaction(
                user,
                result.getAmount(),
                type,
                result.getCategory(),
                result.getDescription(),
                message.getChatId(),
                null,
                null,
                message.getMessageId()  // 传递 Telegram 消息 ID
        );
        
        return String.format(
                "✅ 支出已记录\n\n金额：¥%s\n分类：%s\n备注：%s",
                result.getAmount(),
                result.getCategory(),
                result.getDescription()
        );
    }
    
    public String executeNatural(Message message) {
        User user = userService.getOrCreateUser(message);
        MessageParser.ParseResult result = messageParser.parse(message.getText());
        
        if (!result.isSuccess()) {
            return null; // 不回复，让其他处理器处理
        }
        
        transactionService.addTransaction(
                user,
                result.getAmount(),
                result.getType(),
                result.getCategory(),
                result.getDescription(),
                message.getChatId(),
                null,
                null,
                message.getMessageId()  // 传递 Telegram 消息 ID
        );
        
        // 群聊场景：只返回简洁确认，不干扰群聊
        String emoji = result.getType() == Transaction.TransactionType.INCOME ? "✅" : "✅";
        String typeStr = result.getType() == Transaction.TransactionType.INCOME ? "入款" : "下发";
        
        return String.format("%s %s %s %s", 
                emoji, 
                typeStr, 
                result.getAmount(), 
                result.getDescription());
    }
    
    /**
     * 记录交易（供操作员使用）
     */
    public void recordTransaction(Message message, BigDecimal amount, String category, String description) {
        recordTransaction(message, amount, category, description, null, null);
    }
    
    /**
     * 记录交易（供操作员使用，带标记员和操作员信息）
     */
    public void recordTransaction(Message message, BigDecimal amount, String category, String description,
                                   Long taggedUserId, Long operatorUserId) {
        User user = userService.getOrCreateUser(message);
        
        // 根据金额正负判断类型
        Transaction.TransactionType type = amount.compareTo(BigDecimal.ZERO) >= 0 
                ? Transaction.TransactionType.INCOME 
                : Transaction.TransactionType.EXPENSE;
        
        transactionService.addTransaction(
                user,
                amount.abs(),
                type,
                category,
                description,
                message.getChatId(),
                taggedUserId,
                operatorUserId,
                message.getMessageId()  // 传递 Telegram 消息 ID
        );
    }
}
