package com.bot.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bot.accounting.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {
    
    @Select("SELECT t.* FROM transactions t " +
            "JOIN users u ON t.user_id = u.id " +
            "WHERE u.telegram_id = #{telegramId} " +
            "ORDER BY t.transaction_date DESC")
    List<Transaction> findByUserTelegramIdOrderByTransactionDateDesc(@Param("telegramId") Long telegramId);
    
    @Select("SELECT t.* FROM transactions t " +
            "JOIN users u ON t.user_id = u.id " +
            "WHERE u.telegram_id = #{telegramId} " +
            "AND t.transaction_date BETWEEN #{startDate} AND #{endDate} " +
            "ORDER BY t.transaction_date DESC")
    List<Transaction> findByUserTelegramIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            @Param("telegramId") Long telegramId, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
    
    @Select("SELECT * FROM transactions WHERE chat_id = #{chatId} ORDER BY transaction_date DESC")
    List<Transaction> findByChatIdOrderByTransactionDateDesc(@Param("chatId") Long chatId);
    
    @Select("SELECT * FROM transactions WHERE chat_id = #{chatId} " +
            "AND transaction_date BETWEEN #{startDate} AND #{endDate} " +
            "ORDER BY created_at DESC")
    List<Transaction> findByChatIdAndTransactionDateBetweenOrderByCreatedAtDesc(
            @Param("chatId") Long chatId, 
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
    
    @Select("SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE chat_id = #{chatId} AND type = #{type} " +
            "AND transaction_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal sumAmountByChatAndTypeAndDateBetween(@Param("chatId") Long chatId, 
                                                     @Param("type") String type,
                                                     @Param("startDate") LocalDate startDate, 
                                                     @Param("endDate") LocalDate endDate);
    
    @Select("SELECT * FROM transactions WHERE chat_id = #{chatId} AND type = #{type} " +
            "AND transaction_date BETWEEN #{startDate} AND #{endDate} ORDER BY created_at DESC")
    List<Transaction> findByChatAndTypeAndDateBetween(@Param("chatId") Long chatId,
                                                       @Param("type") String type,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);
    
    @Select("SELECT COALESCE(SUM(t.amount), 0) FROM transactions t " +
            "JOIN users u ON t.user_id = u.id " +
            "WHERE u.telegram_id = #{telegramId} AND t.type = #{type} " +
            "AND t.transaction_date BETWEEN #{startDate} AND #{endDate}")
    BigDecimal sumAmountByUserAndTypeAndDateBetween(@Param("telegramId") Long telegramId, 
                                                     @Param("type") String type,
                                                     @Param("startDate") LocalDate startDate, 
                                                     @Param("endDate") LocalDate endDate);
    
    @Select("SELECT category, COALESCE(SUM(amount), 0) as total FROM transactions t " +
            "JOIN users u ON t.user_id = u.id " +
            "WHERE u.telegram_id = #{telegramId} AND t.type = #{type} " +
            "AND t.transaction_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY t.category")
    List<java.util.Map<String, Object>> sumByCategory(@Param("telegramId") Long telegramId,
                                                       @Param("type") String type,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);
    
    @Select("SELECT COUNT(*) FROM transactions t " +
            "JOIN users u ON t.user_id = u.id " +
            "WHERE u.telegram_id = #{telegramId} " +
            "AND t.transaction_date BETWEEN #{startDate} AND #{endDate}")
    long countByUserTelegramIdAndTransactionDateBetween(@Param("telegramId") Long telegramId, 
                                                         @Param("startDate") LocalDate startDate, 
                                                         @Param("endDate") LocalDate endDate);
}
