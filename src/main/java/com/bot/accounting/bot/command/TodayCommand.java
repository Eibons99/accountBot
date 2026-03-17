package com.bot.accounting.bot.command;

import com.bot.accounting.dto.SummaryDTO;
import com.bot.accounting.dto.TransactionDTO;
import com.bot.accounting.service.DayCutoffService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TodayCommand implements BotCommand {
    
    private final TransactionService transactionService;
    private final SpreadsheetService spreadsheetService;  // 添加 Excel 生成服务
    private final DayCutoffService dayCutoffService;  // 添加日切服务
    
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
        
        // 检查是否已设置日切时间
        if (!dayCutoffService.isDayCutoffTimeSet(chatId)) {
            // 未设置日切时间，提示用户
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText("⚠️ <b>请先设置日切时间</b>\n\n" +
                    "使用指令：<code>设置日切X</code>（X为0-23的数字）\n" +
                    "例如：<code>设置日切14</code> 表示每天14:00开始新账期\n\n" +
                    "设置后机器人将按日切时间切分新旧账单。");
            sendMessage.setParseMode("HTML");
            return sendMessage;
        }
        
        SummaryDTO summary = transactionService.getTodaySummaryForChat(chatId);

        // 简短格式：2026年03月16日 14:00
        String dateStr = summary.getAccountingDate().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        String cutoffTimeStr = dayCutoffService.getDayCutoffTime(chatId).toString();
        String dateTimeStr = dateStr + " " + cutoffTimeStr;
        
        // 按备注分组统计
        String groupByDescription = formatGroupByDescription(summary.getIncomeTransactions());

        // 优化消息格式
        String text = String.format(
                "📊 <b>今日统计</b>\n" +
                "日切时间：%s\n\n" +
                "💰 今日入款（%d笔）\n" +
                "%s" +
                "─────────────────\n" +
                "%s" +
                "总入款：%s",
                dateTimeStr,
                summary.getIncomeCount(),
                formatTransactionList(summary.getIncomeTransactions()),
                groupByDescription,
                summary.getFormattedIncome()
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

        // transactions 已经是按 created_at DESC 排序的（最新的在前面）
        // 取最近 6 条
        int limit = Math.min(transactions.size(), 6);
        List<TransactionDTO> latestTransactions = transactions.subList(0, limit);

        StringBuilder sb = new StringBuilder();

        // 如果总记录数超过 6 条，在顶部显示省略号
        if (transactions.size() > 6) {
            sb.append("……\n");
        }

        // 反转列表，让最新的记录显示在最下面
        for (int i = latestTransactions.size() - 1; i >= 0; i--) {
            TransactionDTO t = latestTransactions.get(i);
            String time = t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String amount = t.getAmount().toString();

            // 只显示操作员（不关心标记员，只关心操作员回复了什么）
            String label;
            if (t.getOperatorName() != null && !t.getOperatorName().isEmpty()) {
                label = t.getOperatorName();
            } else {
                label = "未知操作员";
            }

            // 如果有消息链接，添加点击跳转功能（方便对账回溯）
            if (t.getMessageLink() != null) {
                sb.append(String.format("%s <a href=\"%s\">%s %s</a>\n",
                        time, t.getMessageLink(), amount, label));
            } else {
                sb.append(String.format("%s %s %s\n", time, amount, label));
            }
        }

        // 在底部显示统计提示
        if (transactions.size() > 6) {
            sb.append(String.format("<i>共 %d 条，显示最近 6 条</i>", transactions.size()));
        }
        
        return sb.toString();
    }
    
    /**
     * 按备注分组统计
     */
    private String formatGroupByDescription(List<TransactionDTO> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return "";
        }
        
        // 按备注分组汇总
        Map<String, BigDecimal> groupMap = new HashMap<>();
        for (TransactionDTO t : transactions) {
            String desc = t.getDescription();
            // 清理备注格式
            desc = cleanDescription(desc);
            BigDecimal current = groupMap.getOrDefault(desc, BigDecimal.ZERO);
            groupMap.put(desc, current.add(t.getAmount()));
        }
        
        // 构建分组统计字符串
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BigDecimal> entry : groupMap.entrySet()) {
            sb.append(String.format("%s\n收款总计：%s\n", entry.getKey(), entry.getValue()));
        }
        
        return sb.toString();
    }
    
    /**
     * 清理备注显示格式
     * 去掉花括号和冗余信息
     */
    private String cleanDescription(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "未备注";
        }
        
        // 去掉花括号
        desc = desc.replace("{", "").replace("}", "");
        
        // 处理 "无备注（无标记员）" 格式
        if (desc.contains("无备注") || desc.contains("未备注")) {
            return "未备注";
        }
        
        return desc.trim();
    }
    
    /**
     * 创建下载 Excel 的内联键盘
     */
    private InlineKeyboardMarkup createDownloadKeyboard(Long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardLayout = new ArrayList<>();
        
        // 获取服务器 URL
        String serverUrl = getServerUrl();
        
        // 只有当服务器 URL 不是 localhost 时才显示在线表格按钮
        if (serverUrl != null && !serverUrl.contains("localhost") && !serverUrl.contains("127.0.0.1")) {
            // 第一行：查看在线表格
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton viewOnlineButton = new InlineKeyboardButton();
            viewOnlineButton.setText("📊 查看在线表格");
            
            // 使用日切服务获取当前会计日期
            LocalDate accountingDate = dayCutoffService.getCurrentAccountingDate(chatId);
            String dateStr = accountingDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            viewOnlineButton.setUrl(serverUrl + "/spreadsheet/" + chatId + "/" + dateStr);
            row1.add(viewOnlineButton);
            keyboardLayout.add(row1);
        }
        
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
