package com.bot.accounting.bot;

import com.bot.accounting.config.BotConfig;
import com.bot.accounting.service.ChatLogService;
import com.bot.accounting.service.ChatSyncStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@ConditionalOnProperty(name = "bot.mode", havingValue = "longpolling", matchIfMissing = true)
public class AccountingBot extends TelegramLongPollingBot {
    
    private final BotConfig botConfig;
    private final CommandDispatcher commandDispatcher;
    private final ChatLogService chatLogService;
    private final ChatSyncStateService chatSyncStateService;
    private final CallbackHandler callbackHandler;
    
    public AccountingBot(BotConfig botConfig, CommandDispatcher commandDispatcher, 
                         ChatLogService chatLogService, ChatSyncStateService chatSyncStateService,
                         CallbackHandler callbackHandler) {
        super(createBotOptions(botConfig), botConfig.getToken());
        this.botConfig = botConfig;
        this.commandDispatcher = commandDispatcher;
        this.chatLogService = chatLogService;
        this.chatSyncStateService = chatSyncStateService;
        this.callbackHandler = callbackHandler;
    }
    
    private static DefaultBotOptions createBotOptions(BotConfig config) {
        return new DefaultBotOptions();
    }
    
    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        try {
            // 处理回调查询（内联键盘点击）
            if (update.hasCallbackQuery()) {
                callbackHandler.handleCallback(this, update.getCallbackQuery());
                return;
            }
            
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                
                // 记录收到的所有消息（用于调试）
                log.debug("收到消息：chatId={}, text={}", message.getChatId(), message.getText());
                            
                // 先分发消息，根据返回结果决定是否记录日志
                Object response = commandDispatcher.dispatch(message);
                
                // 调试日志
                log.debug("dispatch 返回：{}", response == null ? "null" : response.getClass().getSimpleName());
                            
                // 只有当有响应时才记录日志和处理
                if (response != null) {
                    // 记录收到的消息（只在需要处理时记录）
                    chatLogService.logReceiveMessage(message);
                                
                    // 检查是否需要返回内联键盘
                    SendMessage keyboardMessage = commandDispatcher.dispatchWithKeyboard(message);
                    if (keyboardMessage != null) {
                        execute(keyboardMessage);
                        chatLogService.logSendMessage(message.getChatId(), keyboardMessage.getText(), message.getMessageId());
                        return;
                    }
                                
                    // 检查是否是 SendMessage 对象（带内联键盘）
                    if (response instanceof SendMessage) {
                        SendMessage sendMessage = (SendMessage) response;
                        execute(sendMessage);
                        chatLogService.logSendMessage(
                            Long.parseLong(sendMessage.getChatId()), 
                            sendMessage.getText(), 
                            message.getMessageId()
                        );
                    } else if (response instanceof String) {
                        sendMessage(message.getChatId(), (String) response, message.getMessageId());
                    }
                                
                    // 记录最后处理的消息 ID（只在成功处理时更新）
                    chatSyncStateService.updateLastProcessedMessageId(
                            message.getChatId(), message.getMessageId());
                }
                // 如果 response 为 null，说明是普通聊天消息，不记录日志和数据库
            }
        } catch (Exception e) {
            log.error("处理消息时发生错误: {}", e.getMessage(), e);
        }
    }
    
    private void sendMessage(Long chatId, String text, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        
        try {
            execute(message);
            // 记录发送的消息
            chatLogService.logSendMessage(chatId, text, replyToMessageId);
        } catch (TelegramApiException e) {
            log.error("发送消息失败: {}", e.getMessage());
        }
    }
}
