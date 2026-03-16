package com.bot.accounting.bot.command;

import com.bot.accounting.dto.SummaryDTO;
import com.bot.accounting.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class MonthCommand implements BotCommand {
    
    private final TransactionService transactionService;
    
    @Override
    public String execute(Message message) {
        Long chatId = message.getChatId();
        // 使用今日统计作为示例（实际应该实现月度统计）
        SummaryDTO summary = transactionService.getTodaySummaryForChat(chatId);
        
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月"));
        
        return String.format(
                "📊 <b>本月统计 (%s)</b>\n\n" +
                "💰 入款总额：%s（%d笔）\n" +
                "💸 下发总额：%s（%d笔）\n\n" +
                "───────────────\n" +
                "汇率：%s\n" +
                "交易费率：%s%%\n\n" +
                "应下发：%s\n" +
                "已下发：%s\n" +
                "未下发：%s",
                month,
                summary.getFormattedIncome(),
                summary.getIncomeCount(),
                summary.getFormattedExpense(),
                summary.getExpenseCount(),
                summary.getExchangeRate(),
                summary.getFeeRate().multiply(new java.math.BigDecimal("100")),
                summary.getFormattedShouldPay(),
                summary.getFormattedPaid(),
                summary.getFormattedUnpaid()
        );
    }
}
