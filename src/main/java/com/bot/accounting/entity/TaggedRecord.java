package com.bot.accounting.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tagged_records")
public class TaggedRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 标记人员（发送消息的人）
    @TableField("tagged_user_id")
    private Long taggedUserId;
    
    // 操作人员（实际记录的人，通常是回复或操作的人）
    @TableField("operator_user_id")
    private Long operatorUserId;
    
    // 金额
    @TableField("amount")
    private BigDecimal amount;
    
    // 标记内容（如 "491 杨芳芳"）
    @TableField("tag_content")
    private String tagContent;
    
    // 群聊ID
    @TableField("chat_id")
    private Long chatId;
    
    // 原始消息ID
    @TableField("message_id")
    private Integer messageId;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableLogic
    @TableField("deleted")
    private Integer deleted = 0;
    
    // 非数据库字段，用于关联查询
    @TableField(exist = false)
    private User taggedUser;
    
    @TableField(exist = false)
    private User operatorUser;
}
