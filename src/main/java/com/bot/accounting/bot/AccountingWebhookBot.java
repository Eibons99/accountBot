package com.bot.accounting.bot;

import com.bot.accounting.config.BotConfig;
import com.bot.accounting.service.ChatLogService;
import com.bot.accounting.service.ChatSyncStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;



@Slf4j
@Component
@ConditionalOnProperty(name = "bot.mode", havingValue = "webhook")
public class AccountingWebhookBot extends TelegramWebhookBot {

    private final BotConfig botConfig;
    private final CommandDispatcher commandDispatcher;
    private final ChatLogService chatLogService;
    private final ChatSyncStateService chatSyncStateService;

    public AccountingWebhookBot(BotConfig botConfig, CommandDispatcher commandDispatcher,
                                 ChatLogService chatLogService, ChatSyncStateService chatSyncStateService) {
        super(createBotOptions(botConfig), botConfig.getToken());
        this.botConfig = botConfig;
        this.commandDispatcher = commandDispatcher;
        this.chatLogService = chatLogService;
        this.chatSyncStateService = chatSyncStateService;
    }

    private static DefaultBotOptions createBotOptions(BotConfig config) {
        return new DefaultBotOptions();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }

    @Override
    public String getBotPath() {
        return botConfig.getWebhookPath();
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();

                // 记录收到的消息
                chatLogService.logReceiveMessage(message);

                // 处理消息
                Object responseObj = commandDispatcher.dispatch(message);
                                
                // 只处理 String 类型的响应
                if (responseObj instanceof String) {
                    String response = (String) responseObj;
                    if (response != null && !response.isEmpty()) {
                        // 记录最后处理的消息 ID
                        chatSyncStateService.updateLastProcessedMessageId(
                                message.getChatId(), message.getMessageId());
                
                        // 返回发送消息的请求
                        return createSendMessage(message.getChatId(), response, message.getMessageId());
                    }
                } else if (responseObj != null) {
                    // SendMessage 或其他类型，认为已处理（因为已经发送了）
                    log.debug("命令返回非 String 类型，认为已处理：chatId={}, messageId={}", 
                            message.getChatId(), message.getMessageId());
                }
                
                // 记录最后处理的消息 ID（即使没有回复）
                chatSyncStateService.updateLastProcessedMessageId(
                        message.getChatId(), message.getMessageId());
            }
        } catch (Exception e) {
            log.error("处理 Webhook 消息时发生错误: {}", e.getMessage(), e);
        }
        return null;
    }

    private SendMessage createSendMessage(Long chatId, String text, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        message.setReplyToMessageId(replyToMessageId);

        // 记录发送的消息
        chatLogService.logSendMessage(chatId, text, replyToMessageId);

        return message;
    }

    /**
     * 手动发送消息（用于非 webhook 触发的场景）
     */
    public void sendMessage(Long chatId, String text, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        message.setReplyToMessageId(replyToMessageId);

        try {
            execute(message);
            chatLogService.logSendMessage(chatId, text, replyToMessageId);
        } catch (TelegramApiException e) {
            log.error("发送消息失败: {}", e.getMessage());
        }
    }
}
