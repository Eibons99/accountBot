package com.bot.accounting.service;

import com.bot.accounting.config.BotConfig;
import com.bot.accounting.dto.SummaryDTO;
import com.bot.accounting.dto.TransactionDTO;
import com.bot.accounting.entity.Transaction;
import com.bot.accounting.entity.User;
import com.bot.accounting.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private final TransactionMapper transactionMapper;
    private final UserService userService;
    private final BotConfig botConfig;
    private final DayCutoffService dayCutoffService;
    
    @Transactional
    public Transaction addTransaction(User user, BigDecimal amount, Transaction.TransactionType type,
                                       String category, String description, Long chatId,
                                       Long taggedUserId, Long operatorUserId) {
        return addTransaction(user, amount, type, category, description, chatId, taggedUserId, operatorUserId, null, null);
    }
    
    @Transactional
    public Transaction addTransaction(User user, BigDecimal amount, Transaction.TransactionType type,
                                       String category, String description, Long chatId,
                                       Long taggedUserId, Long operatorUserId, Integer telegramMessageId) {
        return addTransaction(user, amount, type, category, description, chatId, taggedUserId, operatorUserId, telegramMessageId, null);
    }

    @Transactional
    public Transaction addTransaction(User user, BigDecimal amount, Transaction.TransactionType type,
                                       String category, String description, Long chatId,
                                       Long taggedUserId, Long operatorUserId, Integer telegramMessageId,
                                       LocalDateTime messageTime) {
        Transaction transaction = new Transaction();
        transaction.setUserId(user.getId());
        transaction.setAmount(amount.abs());
        transaction.setType(type.name());
        transaction.setCategory(category != null ? category : "未分类");
        transaction.setDescription(description);

        // 如果提供了消息时间，使用消息时间计算账期日期；否则使用当前时间
        if (messageTime != null) {
            transaction.setTransactionDate(dayCutoffService.getAccountingDate(chatId, messageTime));
        } else {
            transaction.setTransactionDate(dayCutoffService.getCurrentAccountingDate(chatId));
        }

        transaction.setChatId(chatId);
        transaction.setTaggedUserId(taggedUserId);
        transaction.setOperatorUserId(operatorUserId);
        transaction.setTelegramMessageId(telegramMessageId);
        transaction.setStatus(Transaction.TransactionStatus.PENDING.name());
        
        transactionMapper.insert(transaction);
        return transaction;
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDTO> getRecentTransactions(Long telegramId, int limit) {
        return transactionMapper.findByUserTelegramIdOrderByTransactionDateDesc(telegramId)
                .stream()
                .limit(limit)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactionsByDateRange(Long telegramId, LocalDate startDate, LocalDate endDate) {
        return transactionMapper.findByUserTelegramIdAndTransactionDateBetweenOrderByTransactionDateDesc(
                        telegramId, startDate, endDate)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public SummaryDTO getTodaySummary(Long telegramId) {
        LocalDate today = LocalDate.now();
        return getSummaryByDateRange(telegramId, today, today);
    }
    
    @Transactional(readOnly = true)
    public SummaryDTO getTodaySummaryForChat(Long chatId) {
        // 使用日切时间计算今日的开始和结束时间
        LocalDateTime startTime = dayCutoffService.getTodayStartTime(chatId);
        LocalDateTime endTime = dayCutoffService.getTodayEndTime(chatId);

        log.info("查询今日统计：chatId={}, 时间范围={} 到 {}", chatId, startTime, endTime);

        BigDecimal totalIncome = transactionMapper.sumAmountByChatAndTypeAndTimeBetween(
                chatId, Transaction.TransactionType.INCOME.name(), startTime, endTime);
        BigDecimal totalExpense = transactionMapper.sumAmountByChatAndTypeAndTimeBetween(
                chatId, Transaction.TransactionType.EXPENSE.name(), startTime, endTime);

        List<Transaction> incomeTransactions = transactionMapper.findByChatAndTypeAndTimeBetween(
                chatId, Transaction.TransactionType.INCOME.name(), startTime, endTime);
        List<Transaction> expenseTransactions = transactionMapper.findByChatAndTypeAndTimeBetween(
                chatId, Transaction.TransactionType.EXPENSE.name(), startTime, endTime);

        BigDecimal exchangeRate = botConfig.getExchangeRate();
        BigDecimal feeRate = botConfig.getFeeRate();
        BigDecimal shouldPay = totalIncome.multiply(exchangeRate).multiply(BigDecimal.ONE.subtract(feeRate));

        return SummaryDTO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .incomeCount(incomeTransactions.size())
                .expenseCount(expenseTransactions.size())
                .exchangeRate(exchangeRate)
                .feeRate(feeRate)
                .shouldPay(shouldPay)
                .paid(totalExpense)
                .unpaid(shouldPay.subtract(totalExpense))
                .accountingDate(dayCutoffService.getCurrentAccountingDate(chatId))
                .incomeTransactions(incomeTransactions.stream().map(this::convertToDTO).collect(Collectors.toList()))
                .expenseTransactions(expenseTransactions.stream().map(this::convertToDTO).collect(Collectors.toList()))
                .build();
    }
    
    @Transactional(readOnly = true)
    public SummaryDTO getMonthSummary(Long telegramId) {
        YearMonth yearMonth = YearMonth.now();
        // TODO: 月度统计需要考虑日切时间，目前暂时按自然月计算
        // 如果需要严格按照日切时间分割，需要改用 getMonthStartTime 和 getMonthEndTime
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        return getSummaryByDateRange(telegramId, startDate, endDate);
    }
    
    @Transactional(readOnly = true)
    public SummaryDTO getSummaryByChatAndDateRange(Long chatId, LocalDate startDate, LocalDate endDate) {
        BigDecimal totalIncome = transactionMapper.sumAmountByChatAndTypeAndDateBetween(
                chatId, Transaction.TransactionType.INCOME.name(), startDate, endDate);
        BigDecimal totalExpense = transactionMapper.sumAmountByChatAndTypeAndDateBetween(
                chatId, Transaction.TransactionType.EXPENSE.name(), startDate, endDate);
        
        List<Transaction> incomeTransactions = transactionMapper.findByChatAndTypeAndDateBetween(
                chatId, Transaction.TransactionType.INCOME.name(), startDate, endDate);
        List<Transaction> expenseTransactions = transactionMapper.findByChatAndTypeAndDateBetween(
                chatId, Transaction.TransactionType.EXPENSE.name(), startDate, endDate);
        
        BigDecimal exchangeRate = botConfig.getExchangeRate();
        BigDecimal feeRate = botConfig.getFeeRate();
        BigDecimal shouldPay = totalIncome.multiply(exchangeRate).multiply(BigDecimal.ONE.subtract(feeRate));
        
        return SummaryDTO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .incomeCount(incomeTransactions.size())
                .expenseCount(expenseTransactions.size())
                .exchangeRate(exchangeRate)
                .feeRate(feeRate)
                .shouldPay(shouldPay)
                .paid(totalExpense)
                .unpaid(shouldPay.subtract(totalExpense))
                .incomeTransactions(incomeTransactions.stream().map(this::convertToDTO).collect(Collectors.toList()))
                .expenseTransactions(expenseTransactions.stream().map(this::convertToDTO).collect(Collectors.toList()))
                .build();
    }
    
    @Transactional(readOnly = true)
    public SummaryDTO getSummaryByDateRange(Long telegramId, LocalDate startDate, LocalDate endDate) {
        BigDecimal totalIncome = transactionMapper.sumAmountByUserAndTypeAndDateBetween(
                telegramId, Transaction.TransactionType.INCOME.name(), startDate, endDate);
        BigDecimal totalExpense = transactionMapper.sumAmountByUserAndTypeAndDateBetween(
                telegramId, Transaction.TransactionType.EXPENSE.name(), startDate, endDate);
        
        List<Transaction> transactions = transactionMapper
                .findByUserTelegramIdAndTransactionDateBetweenOrderByTransactionDateDesc(telegramId, startDate, endDate);
        long incomeCount = transactions.stream()
                .filter(t -> Transaction.TransactionType.INCOME.name().equals(t.getType())).count();
        long expenseCount = transactions.stream()
                .filter(t -> Transaction.TransactionType.EXPENSE.name().equals(t.getType())).count();
        
        BigDecimal exchangeRate = botConfig.getExchangeRate();
        BigDecimal feeRate = botConfig.getFeeRate();
        BigDecimal shouldPay = totalIncome.multiply(exchangeRate).multiply(BigDecimal.ONE.subtract(feeRate));
        
        return SummaryDTO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome.subtract(totalExpense))
                .incomeCount((int) incomeCount)
                .expenseCount((int) expenseCount)
                .exchangeRate(exchangeRate)
                .feeRate(feeRate)
                .shouldPay(shouldPay)
                .paid(totalExpense)
                .unpaid(shouldPay.subtract(totalExpense))
                .build();
    }
    
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getCategorySummary(Long telegramId, Transaction.TransactionType type, 
                                                       LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> results = transactionMapper.sumByCategory(telegramId, type.name(), startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("category"),
                        row -> (BigDecimal) row.get("total")
                ));
    }
    
    @Transactional
    public void deleteTransaction(Long transactionId) {
        transactionMapper.deleteById(transactionId);
    }
    
    private TransactionDTO convertToDTO(Transaction transaction) {
        User user = userService.findById(transaction.getUserId());

        // 查询标记员名字
        String taggedUserName = null;
        if (transaction.getTaggedUserId() != null) {
            User taggedUser = userService.findById(transaction.getTaggedUserId());
            taggedUserName = userService.getUserDisplayName(taggedUser);
        }

        // 查询操作员名字
        String operatorName = null;
        if (transaction.getOperatorUserId() != null) {
            User operatorUser = userService.findById(transaction.getOperatorUserId());
            operatorName = userService.getUserDisplayName(operatorUser);
        }

        return TransactionDTO.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .type(Transaction.TransactionType.valueOf(transaction.getType()))
                .category(transaction.getCategory())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .createdAt(transaction.getCreatedAt())
                .userName(userService.getUserDisplayName(user))
                .taggedUserName(taggedUserName)
                .operatorName(operatorName)
                .telegramMessageId(transaction.getTelegramMessageId())
                .chatId(transaction.getChatId())
                .build();
    }
}
