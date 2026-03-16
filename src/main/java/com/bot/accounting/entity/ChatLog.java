package com.bot.accounting.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_logs")
public class ChatLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 消息ID
    @TableField("message_id")
    private Integer messageId;
    
    // 用户ID
    @TableField("user_id")
    private Long userId;
    
    // 用户名
    @TableField("username")
    private String username;
    
    // 用户昵称
    @TableField("nickname")
    private String nickname;
    
    // 群聊ID
    @TableField("chat_id")
    private Long chatId;
    
    // 群聊名称
    @TableField("chat_title")
    private String chatTitle;
    
    // 消息内容
    @TableField("message_text")
    private String messageText;
    
    // 机器人类型：RECEIVE=收到消息，SEND=发送消息
    @TableField("log_type")
    private String logType;
    
    // 回复的消息ID
    @TableField("reply_to_message_id")
    private Integer replyToMessageId;
    
    // 创建时间
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // 逻辑删除字段
    @TableLogic
    @TableField("deleted")
    private Integer deleted = 0;
    
    public enum LogType {
        RECEIVE, SEND
    }
}
