package com.bot.accounting.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SummaryDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal balance;
    private int incomeCount;
    private int expenseCount;
    private BigDecimal exchangeRate;
    private BigDecimal feeRate;
    private BigDecimal shouldPay;
    private BigDecimal paid;
    private BigDecimal unpaid;
    private List<TransactionDTO> incomeTransactions;
    private List<TransactionDTO> expenseTransactions;
    
    public String getFormattedIncome() {
        return totalIncome.toString();
    }
    
    public String getFormattedExpense() {
        return totalExpense.toString();
    }
    
    public String getFormattedBalance() {
        return balance.toString();
    }
    
    public String getFormattedShouldPay() {
        return shouldPay.toString();
    }
    
    public String getFormattedPaid() {
        return paid.toString();
    }
    
    public String getFormattedUnpaid() {
        return unpaid.toString();
    }
}
