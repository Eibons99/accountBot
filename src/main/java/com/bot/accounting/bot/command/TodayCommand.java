package com.bot.accounting.bot.command;

import com.bot.accounting.dto.SummaryDTO;
import com.bot.accounting.dto.TransactionDTO;
import com.bot.accounting.service.SpreadsheetService;
import com.bot.accounting.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TodayCommand implements BotCommand {
    
    private final TransactionService transactionService;
    private final SpreadsheetService spreadsheetService;  // 添加 Excel 生成服务
    
    @Override
    public String execute(Message message) {
        // 这个方法不再使用，改为使用 executeWithKeyboard
        return executeWithKeyboard(message).getText();
    }
        
    /**
     * 执行并返回带内联键盘的消息
     */
    public SendMessage executeWithKeyboard(Message message) {
        Long chatId = message.getChatId();
        SummaryDTO summary = transactionService.getTodaySummaryForChat(chatId);
            
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日"));
            
        String text = String.format(
                "📊 <b>今日统计 (%s)</b>\n\n" +
                "💰 今日入款（%d笔）\n%s\n\n" +
                "💸 今日下发（%d笔）\n%s\n\n" +
                "───────────────\n" +
                "总入款：%s\n" +
                "汇率：%s\n" +
                "交易费率：%s%%\n\n" +
                "应下发：%s\n" +
                "已下发：%s\n" +
                "未下发：%s",
                today,
                summary.getIncomeCount(),
                formatTransactionList(summary.getIncomeTransactions()),
                summary.getExpenseCount(),
                formatTransactionList(summary.getExpenseTransactions()),
                summary.getFormattedIncome(),
                summary.getExchangeRate(),
                summary.getFeeRate().multiply(new BigDecimal("100")),
                summary.getFormattedShouldPay(),
                summary.getFormattedPaid(),
                summary.getFormattedUnpaid()
        );
            
        // 创建 SendMessage
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML");
            
        // 添加内联键盘（下载按钮）
        InlineKeyboardMarkup keyboard = createDownloadKeyboard(chatId);
        sendMessage.setReplyMarkup(keyboard);
            
        return sendMessage;
    }
    
    private String formatTransactionList(List<TransactionDTO> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TransactionDTO t : transactions) {
            String time = t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String amount = t.getAmount().toString();
            String description = t.getDescription();
            
            // 如果有消息链接，添加点击跳转功能
            if (t.getMessageLink() != null) {
                sb.append(String.format("%s <a href=\"%s\">%s %s</a>\n", 
                        time, t.getMessageLink(), amount, description));
            } else {
                sb.append(String.format("%s %s %s\n", time, amount, description));
            }
        }
        return sb.toString().trim();
    }
    
    /**
     * 创建下载 Excel 的内联键盘
     */
    private InlineKeyboardMarkup createDownloadKeyboard(Long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardLayout = new ArrayList<>();
        
        // 第一行：查看在线表格
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton viewOnlineButton = new InlineKeyboardButton();
        viewOnlineButton.setText("📊 查看在线表格");
        
        // 构建在线表格 URL（需要配置服务器地址）
        String serverUrl = getServerUrl();
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        viewOnlineButton.setUrl(serverUrl + "/spreadsheet/" + chatId + "/" + dateStr);
        row1.add(viewOnlineButton);
        keyboardLayout.add(row1);
        
        // 第二行：导出更多选项
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton exportButton = new InlineKeyboardButton();
        exportButton.setText("📤 导出更多格式/日期");
        exportButton.setCallbackData("export_menu");
        row2.add(exportButton);
        keyboardLayout.add(row2);
        
        keyboard.setKeyboard(keyboardLayout);
        return keyboard;
    }
    
    /**
     * 获取服务器 URL（从环境变量或配置文件读取）
     */
    private String getServerUrl() {
        // TODO: 从配置文件中读取，例如：application.yml 中的 bot.server-url
        // 示例：http://your-server.com 或 https://your-domain.com
        return System.getenv().getOrDefault("BOT_SERVER_URL", "http://localhost:8080");
    }
}
