package com.bot.accounting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_sync_state")
public class ChatSyncState {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("chat_id")
    private Long chatId;
    
    @TableField("last_message_id")
    private Integer lastMessageId;
    
    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;
    
    @TableField("need_full_sync")
    private Boolean needFullSync;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
