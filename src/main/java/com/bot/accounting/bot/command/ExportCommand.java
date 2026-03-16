package com.bot.accounting.bot.command;

import com.bot.accounting.service.SpreadsheetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportCommand implements BotCommand {
    
    private final SpreadsheetService spreadsheetService;
    
    @Override
    public String execute(Message message) {
        // 这个方法不会被调用，因为我们使用 sendDocument 方式
        return "请使用 /export 命令导出账单";
    }
    
    /**
     * 导出并发送 Excel/CSV 文件
     * @return Object（SendDocument 或 SendMessage），如果失败则返回 SendMessage
     */
    public Object exportAndSendFile(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        
        log.info("收到导出请求：chatId={}, text={}", chatId, text);
        
        try {
            // 解析日期范围和格式
            LocalDate startDate;
            LocalDate endDate;
            String format = "excel"; // 默认 Excel
            
            if (text.contains(" ")) {
                String[] parts = text.split("\\s+", 3);
                
                // 检查是否指定了格式
                if (parts[1].equalsIgnoreCase("csv") || parts[1].equalsIgnoreCase("excel")) {
                    format = parts[1].toLowerCase();
                    
                    // 如果有第三个参数，解析日期
                    if (parts.length > 2) {
                        DateRange dateRange = parseDateRange(parts[2]);
                        startDate = dateRange.getStart();
                        endDate = dateRange.getEnd();
                    } else {
                        // 默认本月
                        YearMonth yearMonth = YearMonth.now();
                        startDate = yearMonth.atDay(1);
                        endDate = yearMonth.atEndOfMonth();
                    }
                } else {
                    // 没有指定格式，直接解析日期
                    DateRange dateRange = parseDateRange(parts[1] + (parts.length > 2 ? " " + parts[2] : ""));
                    startDate = dateRange.getStart();
                    endDate = dateRange.getEnd();
                }
            } else {
                // 默认导出本月
                YearMonth yearMonth = YearMonth.now();
                startDate = yearMonth.atDay(1);
                endDate = yearMonth.atEndOfMonth();
            }
            
            // 生成文件
            byte[] fileData;
            String fileName;
            
            if (format.equals("csv")) {
                fileData = spreadsheetService.generateCsv(chatId, startDate, endDate);
                fileName = generateFileName(chatId, startDate, endDate, "csv");
            } else {
                fileData = spreadsheetService.generateExcel(chatId, startDate, endDate);
                fileName = generateFileName(chatId, startDate, endDate, "xlsx");
            }
            
            // 创建 InputFile
            InputFile inputFile = new InputFile();
            inputFile.setMedia(new java.io.ByteArrayInputStream(fileData), fileName);
            
            // 创建 SendDocument
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId.toString());
            sendDocument.setDocument(inputFile);
            sendDocument.setCaption(String.format(
                "✅ 账单已导出\n\n" +
                "📄 格式：%s\n" +
                "📅 范围：%s 至 %s\n" +
                "💡 提示：文件可直接在 Excel 中打开",
                format.toUpperCase(),
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ));
            
            log.info("文件生成成功：{}, 大小：{} bytes", fileName, fileData.length);
            return sendDocument;
            
        } catch (DateTimeParseException e) {
            log.error("日期解析失败", e);
            // 返回错误消息
            return createErrorMessage("❌ 日期格式错误\n\n示例：\n/export - 导出本月\n/export 2024-01 - 导出 2024 年 1 月\n/export csv 2024-01-01 2024-01-31");
        } catch (Exception e) {
            log.error("导出失败", e);
            return createErrorMessage("❌ 导出失败：" + e.getMessage());
        }
    }
    
    /**
     * 创建错误消息
     */
    private org.telegram.telegrambots.meta.api.methods.send.SendMessage createErrorMessage(String text) {
        org.telegram.telegrambots.meta.api.methods.send.SendMessage message = 
            new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
        message.setParseMode("HTML");
        return message;
    }
    
    /**
     * 解析日期范围
     */
    private DateRange parseDateRange(String dateStr) {
        dateStr = dateStr.trim();
        
        // 尝试解析单个年月（如：2024-01）
        try {
            YearMonth yearMonth = YearMonth.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM"));
            return new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        } catch (Exception e) {
            // 忽略，继续尝试其他格式
        }
        
        // 尝试解析日期范围（如：2024-01-01 2024-01-31）
        if (dateStr.contains(" ")) {
            String[] parts = dateStr.split("\\s+");
            if (parts.length == 2) {
                try {
                    LocalDate start = LocalDate.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE);
                    LocalDate end = LocalDate.parse(parts[1], DateTimeFormatter.ISO_LOCAL_DATE);
                    return new DateRange(start, end);
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
        
        // 都失败了，抛出异常
        throw new DateTimeParseException("无法解析日期：" + dateStr, dateStr, 0);
    }
    
    /**
     * 生成文件名
     */
    private String generateFileName(Long chatId, LocalDate start, LocalDate end, String ext) {
        String timestamp = java.time.LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("账单_%s_%s_to_%s.%s",
            start.format(DateTimeFormatter.ISO_LOCAL_DATE),
            end.format(DateTimeFormatter.ISO_LOCAL_DATE),
            timestamp,
            ext
        );
    }
    
    /**
     * 日期范围内部类
     */
    private static class DateRange {
        private final LocalDate start;
        private final LocalDate end;
        
        public DateRange(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
        
        public LocalDate getStart() {
            return start;
        }
        
        public LocalDate getEnd() {
            return end;
        }
    }
}
