package com.bot.accounting.bot.command;

import com.bot.accounting.entity.Transaction;
import com.bot.accounting.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CategoryCommand implements BotCommand {
    
    private final TransactionService transactionService;
    
    @Override
    public String execute(Message message) {
        Long telegramId = message.getFrom().getId();
        YearMonth yearMonth = YearMonth.now();
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        Map<String, BigDecimal> expenseByCategory = transactionService.getCategorySummary(
                telegramId, Transaction.TransactionType.EXPENSE, startDate, endDate);
        Map<String, BigDecimal> incomeByCategory = transactionService.getCategorySummary(
                telegramId, Transaction.TransactionType.INCOME, startDate, endDate);
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>本月分类统计</b>\n\n");
        
        // 支出分类
        if (!expenseByCategory.isEmpty()) {
            sb.append("💸 <b>支出分类</b>\n");
            expenseByCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(entry -> {
                        sb.append(String.format("  • %s：¥%s\n", entry.getKey(), entry.getValue()));
                    });
            sb.append("\n");
        }
        
        // 收入分类
        if (!incomeByCategory.isEmpty()) {
            sb.append("💰 <b>收入分类</b>\n");
            incomeByCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(entry -> {
                        sb.append(String.format("  • %s：¥%s\n", entry.getKey(), entry.getValue()));
                    });
        }
        
        if (expenseByCategory.isEmpty() && incomeByCategory.isEmpty()) {
            sb.append("暂无分类数据\n\n使用 /add 或自然语言添加记录。");
        }
        
        return sb.toString();
    }
}
