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

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class IncomeCommand implements BotCommand {
    
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
        
        // 强制设置为收入
        Transaction.TransactionType type = Transaction.TransactionType.INCOME;
        
        transactionService.addTransaction(
                user,
                result.getAmount(),
                type,
                result.getCategory(),
                result.getDescription(),
                message.getChatId(),
                null,
                null,
                message.getMessageId(),  // 传递 Telegram 消息 ID
                getMessageTime(message)  // 传递消息时间
        );
        
        return String.format(
                "✅ 收入已记录\n\n金额：¥%s\n分类：%s\n备注：%s",
                result.getAmount(),
                result.getCategory(),
                result.getDescription()
        );
    }

    /**
     * 从 Telegram Message 获取消息时间
     */
    private LocalDateTime getMessageTime(Message message) {
        if (message.getDate() != null) {
            return dayCutoffService.toLocalDateTime(message.getDate());
        }
        return null;
    }
}
