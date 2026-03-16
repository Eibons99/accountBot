package com.bot.accounting.service;

import com.bot.accounting.entity.ChatLog;
import com.bot.accounting.mapper.ChatLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLogService {
    
    private final ChatLogMapper chatLogMapper;
    
    /**
     * 记录收到的消息
     */
    public void logReceiveMessage(Message message) {
        try {
            ChatLog chatLog = new ChatLog();
            chatLog.setMessageId(message.getMessageId());
            chatLog.setUserId(message.getFrom().getId());
            chatLog.setUsername(message.getFrom().getUserName());
            
            // 构建昵称
            String nickname = message.getFrom().getFirstName();
            if (message.getFrom().getLastName() != null) {
                nickname += " " + message.getFrom().getLastName();
            }
            chatLog.setNickname(nickname);
            
            chatLog.setChatId(message.getChatId());
            chatLog.setChatTitle(message.getChat().getTitle());
            chatLog.setMessageText(message.getText());
            chatLog.setLogType(ChatLog.LogType.RECEIVE.name());
            
            if (message.getReplyToMessage() != null) {
                chatLog.setReplyToMessageId(message.getReplyToMessage().getMessageId());
            }
            
            chatLogMapper.insert(chatLog);
        } catch (Exception e) {
            log.error("记录接收消息日志失败: {}", e.getMessage());
        }
    }
    
    /**
     * 记录发送的消息
     */
    public void logSendMessage(Long chatId, String text, Integer replyToMessageId) {
        try {
            ChatLog chatLog = new ChatLog();
            chatLog.setMessageId(-1); // 发送的消息没有ID
            chatLog.setUserId(0L); // 机器人自己
            chatLog.setUsername("bot");
            chatLog.setNickname("记账机器人");
            chatLog.setChatId(chatId);
            chatLog.setChatTitle("");
            chatLog.setMessageText(text);
            chatLog.setLogType(ChatLog.LogType.SEND.name());
            chatLog.setReplyToMessageId(replyToMessageId);
            
            chatLogMapper.insert(chatLog);
        } catch (Exception e) {
            log.error("记录发送消息日志失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取最近的聊天记录
     */
    public List<ChatLog> getRecentLogs(Long chatId, int limit) {
        return chatLogMapper.findRecentByChatId(chatId, limit);
    }
    
    /**
     * 获取今日的聊天记录
     */
    public List<ChatLog> getTodayLogs(Long chatId) {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return chatLogMapper.findByChatIdAndTimeRange(chatId, startOfDay, endOfDay);
    }
    
    /**
     * 获取用户的聊天记录
     */
    public List<ChatLog> getUserLogs(Long userId, int limit) {
        return chatLogMapper.findRecentByUserId(userId, limit);
    }
    
    /**
     * 获取所有聊天记录（按时间范围）
     */
    public List<ChatLog> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return chatLogMapper.findByTimeRange(startTime, endTime);
    }
}
