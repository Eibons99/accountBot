package com.bot.accounting.service;

import com.bot.accounting.entity.Transaction;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MessageParser {
    
    // 匹配金额的正则表达式 - 支持 +金额 或 金额
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\+?(\\d+(?:\\.\\d{1,2})?)");
    
    // 下发关键词
    private static final String[] EXPENSE_KEYWORDS = {"下发", "支付", "付款", "转账", "转", "出款"};
    
    // 入款关键词
    private static final String[] INCOME_KEYWORDS = {"收", "收入", "入款", "到账", "+"};
    
    @Data
    @Builder
    public static class ParseResult {
        private boolean success;
        private BigDecimal amount;
        private Transaction.TransactionType type;
        private String category;
        private String description;
        private String errorMessage;
    }
    
    public ParseResult parse(String message) {
        // 去除命令前缀
        String text = message.trim();
        if (text.startsWith("/")) {
            int spaceIndex = text.indexOf(' ');
            if (spaceIndex > 0) {
                text = text.substring(spaceIndex + 1).trim();
            } else {
                return ParseResult.builder()
                        .success(false)
                        .errorMessage("请输入金额和描述，例如：/add 100 午餐")
                        .build();
            }
        }
        
        // 检查是否是特殊指令（如设置日切等），不解析为记账消息
        if (isSpecialCommand(text)) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("非记账格式")
                    .build();
        }
        
        // 提取金额
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(text);
        if (!amountMatcher.find()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("无法识别金额，请输入数字，例如：100")
                    .build();
        }
        
        BigDecimal amount = new BigDecimal(amountMatcher.group(1));
        
        // 判断收支类型
        Transaction.TransactionType type = determineType(message);
        
        // 提取描述
        String description = extractDescription(text, amountMatcher);
        
        // 自动分类
        String category = autoCategorize(description, type);
        
        return ParseResult.builder()
                .success(true)
                .amount(amount)
                .type(type)
                .category(category)
                .description(description)
                .build();
    }
    
    /**
     * 检查是否是特殊指令（不应该被解析为记账消息）
     */
    private boolean isSpecialCommand(String text) {
        String lowerText = text.toLowerCase();
        
        // 日切相关指令
        if (lowerText.contains("日切") || lowerText.contains("cutoff")) {
            return true;
        }
        
        // 汇率相关指令
        if (lowerText.contains("汇率") || lowerText.contains("rate")) {
            return true;
        }
        
        // 费率相关指令
        if (lowerText.contains("费率") || lowerText.contains("fee")) {
            return true;
        }
        
        // 管理员设置类指令
        if (lowerText.startsWith("设置") || lowerText.startsWith("取消")) {
            return true;
        }
        
        return false;
    }
    
    private Transaction.TransactionType determineType(String message) {
        String text = message.trim();
        
        // 如果以 + 开头，判定为入款
        if (text.startsWith("+")) {
            return Transaction.TransactionType.INCOME;
        }
        
        String lowerMsg = message.toLowerCase();
        
        // 检查下发关键词
        for (String keyword : EXPENSE_KEYWORDS) {
            if (lowerMsg.contains(keyword)) {
                return Transaction.TransactionType.EXPENSE;
            }
        }
        
        // 检查入款关键词
        for (String keyword : INCOME_KEYWORDS) {
            if (lowerMsg.contains(keyword)) {
                return Transaction.TransactionType.INCOME;
            }
        }
        
        // 默认入款（群聊场景主要是收付款）
        return Transaction.TransactionType.INCOME;
    }
    
    private String extractDescription(String text, Matcher amountMatcher) {
        // 移除金额部分，剩下的作为描述
        String beforeAmount = text.substring(0, amountMatcher.start()).trim();
        String afterAmount = text.substring(amountMatcher.end()).trim();
        
        // 清理常见词汇
        String[] wordsToRemove = {"元", "块", "钱", "+", "入款", "下发", "支付"};
        for (String word : wordsToRemove) {
            beforeAmount = beforeAmount.replace(word, "").trim();
            afterAmount = afterAmount.replace(word, "").trim();
        }
        
        String description = (beforeAmount + " " + afterAmount).trim();
        return description.isEmpty() ? "未备注" : description;
    }
    
    private String autoCategorize(String description, Transaction.TransactionType type) {
        // 群组记账场景，返回描述本身作为分类依据
        if (type == Transaction.TransactionType.INCOME) {
            return "入款";
        } else {
            return "下发";
        }
    }
}
