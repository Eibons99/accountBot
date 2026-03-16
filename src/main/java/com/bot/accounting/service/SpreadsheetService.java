package com.bot.accounting.service;

import com.bot.accounting.dto.TransactionDTO;
import com.bot.accounting.entity.Transaction;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpreadsheetService {
    
    private final TransactionService transactionService;
    
    /**
     * 生成 Excel 账单表格
     */
    public byte[] generateExcel(Long chatId, LocalDate startDate, LocalDate endDate) {
        log.info("生成 Excel 表格：chatId={}, startDate={}, endDate={}", chatId, startDate, endDate);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("账单明细");
            
            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            
            // 写入表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"日期", "时间", "类型", "金额", "分类", "备注", "操作员", "标记员"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 写入数据
            List<TransactionDTO> transactions = transactionService.getTransactionsByDateRange(
                chatId, startDate, endDate);
            
            int rowNum = 1;
            for (TransactionDTO t : transactions) {
                Row row = sheet.createRow(rowNum++);
                
                // 日期
                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(t.getTransactionDate().toString());
                dateCell.setCellStyle(dateStyle);
                
                // 时间
                row.createCell(1).setCellValue(
                    t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                
                // 类型
                row.createCell(2).setCellValue(
                    t.getType() == Transaction.TransactionType.INCOME ? "入款" : "下发");
                
                // 金额
                Cell amountCell = row.createCell(3);
                amountCell.setCellValue(t.getAmount().doubleValue());
                amountCell.setCellStyle(currencyStyle);
                
                // 分类
                row.createCell(4).setCellValue(t.getCategory());
                
                // 备注
                row.createCell(5).setCellValue(t.getDescription());
                
                // 操作员
                row.createCell(6).setCellValue(t.getUserName() != null ? t.getUserName() : "");
                
                // 标记员（这里用 userName 代替，实际应该根据业务需求调整）
                row.createCell(7).setCellValue(t.getUserName() != null ? t.getUserName() : "");
            }
            
            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // 添加汇总行
            addSummaryRow(sheet, transactions, rowNum, currencyStyle);
            
            // 输出为字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            byte[] result = baos.toByteArray();
            
            log.info("Excel 生成成功，共{}条记录，大小：{} bytes", transactions.size(), result.length);
            return result;
            
        } catch (Exception e) {
            log.error("生成 Excel 失败", e);
            throw new RuntimeException("生成 Excel 失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 生成 CSV 账单表格
     */
    public byte[] generateCsv(Long chatId, LocalDate startDate, LocalDate endDate) {
        log.info("生成 CSV 表格：chatId={}, startDate={}, endDate={}", chatId, startDate, endDate);
        
        StringWriter writer = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(writer)) {
            // 写入表头（带 BOM，防止 Excel 打开乱码）
            String[] headers = {"日期", "时间", "类型", "金额", "分类", "备注", "操作员", "标记员"};
            csvWriter.writeNext(headers);
            
            // 写入数据
            List<TransactionDTO> transactions = transactionService.getTransactionsByDateRange(
                chatId, startDate, endDate);
            
            for (TransactionDTO t : transactions) {
                String[] data = {
                    t.getTransactionDate().toString(),
                    t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    t.getType() == Transaction.TransactionType.INCOME ? "入款" : "下发",
                    t.getAmount().toString(),
                    t.getCategory(),
                    t.getDescription(),
                    t.getUserName() != null ? t.getUserName() : "",
                    t.getUserName() != null ? t.getUserName() : ""
                };
                csvWriter.writeNext(data);
            }
            
            // 添加汇总行
            addCsvSummary(csvWriter, transactions);
            
            byte[] result = ("\uFEFF" + writer.toString()).getBytes(StandardCharsets.UTF_8);
            log.info("CSV 生成成功，共{}条记录，大小：{} bytes", transactions.size(), result.length);
            return result;
            
        } catch (Exception e) {
            log.error("生成 CSV 失败", e);
            throw new RuntimeException("生成 CSV 失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 创建表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * 创建货币样式
     */
    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("¥#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
    
    /**
     * 创建日期样式
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("yyyy-mm-dd"));
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
    
    /**
     * 添加汇总行
     */
    private void addSummaryRow(Sheet sheet, List<TransactionDTO> transactions, int rowNum, CellStyle style) {
        if (transactions.isEmpty()) {
            return;
        }
        
        Row summaryRow = sheet.createRow(rowNum);
        summaryRow.createCell(0).setCellValue("汇总");
        
        // 计算入款和下发总额
        double totalIncome = transactions.stream()
            .filter(t -> t.getType() == Transaction.TransactionType.INCOME)
            .mapToDouble(t -> t.getAmount().doubleValue())
            .sum();
        
        double totalExpense = transactions.stream()
            .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
            .mapToDouble(t -> t.getAmount().doubleValue())
            .sum();
        
        summaryRow.createCell(3).setCellValue(totalIncome);
        summaryRow.getCell(3).setCellStyle(style);
        
        Row summaryRow2 = sheet.createRow(rowNum + 1);
        summaryRow2.createCell(3).setCellValue(-totalExpense);
        summaryRow2.getCell(3).setCellStyle(style);
    }
    
    /**
     * 添加 CSV 汇总行
     */
    private void addCsvSummary(CSVWriter csvWriter, List<TransactionDTO> transactions) {
        if (transactions.isEmpty()) {
            return;
        }
        
        // 计算入款和下发总额
        double totalIncome = transactions.stream()
            .filter(t -> t.getType() == Transaction.TransactionType.INCOME)
            .mapToDouble(t -> t.getAmount().doubleValue())
            .sum();
        
        double totalExpense = transactions.stream()
            .filter(t -> t.getType() == Transaction.TransactionType.EXPENSE)
            .mapToDouble(t -> t.getAmount().doubleValue())
            .sum();
        
        csvWriter.writeNext(new String[]{"汇总", "", "入款", String.valueOf(totalIncome), "", "", "", ""});
        csvWriter.writeNext(new String[]{"", "", "下发", String.valueOf(-totalExpense), "", "", "", ""});
    }
}
