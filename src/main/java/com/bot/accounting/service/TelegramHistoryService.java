package com.bot.accounting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramHistoryService {

    private final TelegramLongPollingBot bot;

    // 每次获取的最大消息数
    private static final int BATCH_SIZE = 100;
    // 最大回溯天数
    private static final int MAX_DAYS_BACK = 7;

    /**
     * 获取聊天的历史消息
     * 注意：此方法不能使用 GetUpdates，因为与 Bot 的 LongPolling 冲突
     * @param chatId 聊天ID
     * @param fromMessageId 从哪个消息ID开始（不包含）
     * @return 消息列表（按时间升序）
     */
    public List<Message> getChatHistory(Long chatId, Integer fromMessageId) {
        // 注意：GetUpdates 与 LongPolling Bot 冲突，返回空列表
        // 历史消息恢复应通过其他方式实现
        log.warn("GetUpdates 与 LongPolling Bot 冲突，无法获取历史消息。chatId={}", chatId);
        return new ArrayList<>();
    }

    /**
     * 获取最新的消息ID
     * 注意：此方法不能使用 GetUpdates，因为与 Bot 的 LongPolling 冲突
     */
    public Integer getLatestMessageId(Long chatId) {
        // 注意：GetUpdates 与 LongPolling Bot 冲突
        log.warn("GetUpdates 与 LongPolling Bot 冲突，无法获取最新消息ID。chatId={}", chatId);
        return null;
    }

    /**
     * 检查机器人是否还在群中
     */
    public boolean isBotInChat(Long chatId) {
        try {
            // 尝试获取聊天信息
            org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat getChat = 
                    new org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat();
            getChat.setChatId(chatId.toString());
            bot.execute(getChat);
            return true;
        } catch (TelegramApiException e) {
            log.warn("机器人可能不在群中: chatId={}, error={}", chatId, e.getMessage());
            return false;
        }
    }
}
