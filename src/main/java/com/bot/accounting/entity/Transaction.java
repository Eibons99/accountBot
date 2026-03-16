package com.bot.accounting.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("transactions")
public class Transaction {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("amount")
    private BigDecimal amount;
    
    @TableField("type")
    private String type;
    
    @TableField("category")
    private String category;
    
    @TableField("description")
    private String description;
    
    @TableField("transaction_date")
    private LocalDate transactionDate;
    
    @TableField("chat_id")
    private Long chatId;
    
    @TableField("tagged_user_id")
    private Long taggedUserId;
    
    @TableField("operator_user_id")
    private Long operatorUserId;
    
    // Telegram 消息 ID（用于消息定位）
    @TableField("telegram_message_id")
    private Integer telegramMessageId;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField("status")
    private String status = TransactionStatus.PENDING.name();
    
    @TableLogic
    @TableField("deleted")
    private Integer deleted = 0;
    
    // 非数据库字段，用于关联查询
    @TableField(exist = false)
    private User user;
    
    public enum TransactionType {
        INCOME, EXPENSE
    }
    
    public enum TransactionStatus {
        PENDING, COMPLETED
    }
}
