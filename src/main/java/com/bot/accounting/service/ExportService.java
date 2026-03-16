package com.bot.accounting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {
    
    private final SpreadsheetService spreadsheetService;
    private final FileStorageService fileStorageService;
    
    /**
     * 导出 Excel 并上传到 MinIO
     * @param chatId 聊天 ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 下载链接
     */
    public String exportToExcel(Long chatId, LocalDate startDate, LocalDate endDate) 
            throws Exception {
        log.info("开始导出 Excel: chatId={}, startDate={}, endDate={}", chatId, startDate, endDate);
        
        // 生成 Excel
        byte[] excelData = spreadsheetService.generateExcel(chatId, startDate, endDate);
        
        // 生成文件名
        String fileName = generateFileName(chatId, startDate, endDate, "xlsx");
        
        // 上传到 MinIO
        InputStream inputStream = new ByteArrayInputStream(excelData);
        fileStorageService.uploadFile(inputStream, fileName, 
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        
        // 生成下载链接（有效期 1 小时）
        String downloadUrl = fileStorageService.getPresignedUrl(fileName, 3600);
        
        log.info("Excel 导出成功，下载链接：{}", downloadUrl);
        return downloadUrl;
    }
    
    /**
     * 导出 CSV 并上传到 MinIO
     * @param chatId 聊天 ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 下载链接
     */
    public String exportToCsv(Long chatId, LocalDate startDate, LocalDate endDate) 
            throws Exception {
        log.info("开始导出 CSV: chatId={}, startDate={}, endDate={}", chatId, startDate, endDate);
        
        // 生成 CSV
        byte[] csvData = spreadsheetService.generateCsv(chatId, startDate, endDate);
        
        // 生成文件名
        String fileName = generateFileName(chatId, startDate, endDate, "csv");
        
        // 上传到 MinIO
        InputStream inputStream = new ByteArrayInputStream(csvData);
        fileStorageService.uploadFile(inputStream, fileName, "text/csv");
        
        // 生成下载链接（有效期 1 小时）
        String downloadUrl = fileStorageService.getPresignedUrl(fileName, 3600);
        
        log.info("CSV 导出成功，下载链接：{}", downloadUrl);
        return downloadUrl;
    }
    
    /**
     * 生成文件名
     */
    private String generateFileName(Long chatId, LocalDate start, LocalDate end, String ext) {
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("exports/chat_%d/%s_%s_to_%s.%s",
            chatId,
            start.format(DateTimeFormatter.ISO_LOCAL_DATE),
            end.format(DateTimeFormatter.ISO_LOCAL_DATE),
            timestamp,
            ext
        );
    }
}
