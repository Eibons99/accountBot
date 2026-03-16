package com.bot.accounting.bot.controller;

import com.bot.accounting.dto.TransactionDTO;
import com.bot.accounting.service.SpreadsheetService;
import com.bot.accounting.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/spreadsheet")
@RequiredArgsConstructor
public class SpreadsheetController {

    private final TransactionService transactionService;
    private final SpreadsheetService spreadsheetService;

    /**
     * 显示在线表格页面
     */
    @GetMapping("/{chatId}/{date}")
    public String viewSpreadsheet(
            @PathVariable Long chatId,
            @PathVariable String date,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Model model) {
        
        try {
            // 解析日期
            LocalDate targetDate;
            if (startDate != null && endDate != null) {
                // 使用自定义日期范围
                targetDate = startDate;
            } else {
                // 使用路径中的日期
                targetDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                startDate = targetDate;
                endDate = targetDate;
            }
            
            log.info("查看在线表格：chatId={}, startDate={}, endDate={}", chatId, startDate, endDate);
            
            // 获取交易数据
            List<TransactionDTO> transactions = transactionService.getTransactionsByDateRange(chatId, startDate, endDate);
            
            // 添加到模型
            model.addAttribute("chatId", chatId);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("transactions", transactions);
            model.addAttribute("jsonData", convertToJson(transactions));
            
            return "spreadsheet";
            
        } catch (Exception e) {
            log.error("加载在线表格失败：chatId={}, date={}", chatId, date, e);
            model.addAttribute("error", "加载失败：" + e.getMessage());
            return "error";
        }
    }
    
    /**
     * 下载 Excel 文件
     */
    @GetMapping("/{chatId}/{date}/download")
    public ResponseEntity<byte[]> downloadExcel(
            @PathVariable Long chatId,
            @PathVariable String date,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        
        try {
            // 解析日期
            if (startDate == null || endDate == null) {
                startDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                endDate = startDate;
            }
            
            log.info("下载 Excel: chatId={}, startDate={}, endDate={}", chatId, startDate, endDate);
            
            // 生成 Excel
            byte[] excelData = spreadsheetService.generateExcel(chatId, startDate, endDate);
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String fileName = String.format("账单_%s.xlsx", 
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(excelData.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
                    
        } catch (Exception e) {
            log.error("生成 Excel 失败：chatId={}, date={}", chatId, date, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 转换为 JSON 格式（供前端使用）
     */
    private String convertToJson(List<TransactionDTO> transactions) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < transactions.size(); i++) {
            TransactionDTO t = transactions.get(i);
            json.append("{");
            json.append("\"date\":\"").append(t.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\",");
            json.append("\"time\":\"").append(t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("\",");
            json.append("\"type\":\"").append(t.getType()).append("\",");
            json.append("\"amount\":").append(t.getAmount()).append(",");
            json.append("\"category\":\"").append(escapeJson(t.getCategory())).append("\",");
            json.append("\"description\":\"").append(escapeJson(t.getDescription())).append("\",");
            json.append("\"operator\":\"").append(escapeJson(t.getOperatorName())).append("\",");
            json.append("\"tagger\":\"").append(escapeJson(t.getTaggedUserName())).append("\"");
            json.append("}");
            if (i < transactions.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }
    
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
