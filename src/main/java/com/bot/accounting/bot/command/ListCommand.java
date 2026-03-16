package com.bot.accounting.bot.command;

import com.bot.accounting.dto.TransactionDTO;
import com.bot.accounting.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListCommand implements BotCommand {
    
    private final TransactionService transactionService;
    
    @Override
    public String execute(Message message) {
        Long telegramId = message.getFrom().getId();
        List<TransactionDTO> transactions = transactionService.getRecentTransactions(telegramId, 10);
        
        if (transactions.isEmpty()) {
            return "📭 暂无记账记录\n\n使用 /add 或自然语言（如：花了50买咖啡）来添加记录。";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📋 <b>最近记账记录</b>\n\n");
        
        for (TransactionDTO t : transactions) {
            sb.append(String.format("%s <b>%s</b> %s\n",
                    t.getTypeEmoji(),
                    t.getFormattedAmount(),
                    t.getDescription()));
            sb.append(String.format("   📅 %s  📁 %s  🆔 #%d\n\n",
                    t.getFormattedDate(),
                    t.getCategory(),
                    t.getId()));
        }
        
        sb.append("提示：使用 /delete [ID] 删除记录");
        
        return sb.toString();
    }
}
