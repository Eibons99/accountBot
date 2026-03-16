package com.bot.accounting.bot.command;

import com.bot.accounting.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
@RequiredArgsConstructor
public class DeleteCommand implements BotCommand {
    
    private final TransactionService transactionService;
    
    @Override
    public String execute(Message message) {
        String text = message.getText();
        
        // 提取ID
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            return "❌ 请提供记录ID\n用法：/delete [ID]\n\n使用 /list 查看记录ID";
        }
        
        try {
            Long transactionId = Long.parseLong(parts[1]);
            transactionService.deleteTransaction(transactionId);
            return "✅ 记录 #" + transactionId + " 已删除";
        } catch (NumberFormatException e) {
            return "❌ ID 格式错误，请输入数字";
        } catch (Exception e) {
            return "❌ 删除失败，请确认ID是否正确";
        }
    }
}
