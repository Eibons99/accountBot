package com.bot.accounting.config;

import com.bot.accounting.bot.AccountingBot;
import com.bot.accounting.bot.AccountingWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultWebhook;

import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BotStarterConfig implements ApplicationRunner {

    private final BotConfig botConfig;
    private final Optional<AccountingBot> longPollingBot;
    private final Optional<AccountingWebhookBot> webhookBot;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (botConfig.isWebhookMode()) {
            startWebhookMode();
        } else {
            startLongPollingMode();
        }
    }

    /**
     * 启动 LongPolling 模式
     */
    private void startLongPollingMode() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            longPollingBot.ifPresent(bot -> {
                try {
                    botsApi.registerBot(bot);
                    log.info("机器人已启动（LongPolling 模式）");
                } catch (TelegramApiException e) {
                    log.error("注册 Bot 失败: {}", e.getMessage(), e);
                    throw new RuntimeException("Bot 注册失败", e);
                }
            });
        } catch (TelegramApiException e) {
            log.error("启动 LongPolling Bot 失败: {}", e.getMessage(), e);
            throw new RuntimeException("Bot 启动失败", e);
        }
    }

    /**
     * 启动 Webhook 模式
     */
    private void startWebhookMode() {
        try {
            // 创建 Webhook 实例
            DefaultWebhook webhook = new DefaultWebhook();
            webhook.setInternalUrl("http://localhost:" + botConfig.getServerPort());

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class, webhook);

            webhookBot.ifPresent(bot -> {
                try {
                    // 先设置 Webhook
                    SetWebhook setWebhook = SetWebhook.builder()
                            .url(botConfig.getWebhookUrl() + botConfig.getWebhookPath())
                            .build();
                    botsApi.registerBot(bot, setWebhook);

                    log.info("机器人已启动（Webhook 模式）");
                    log.info("Webhook 地址: {}{}", botConfig.getWebhookUrl(), botConfig.getWebhookPath());
                    log.info("内部服务端口: {}", botConfig.getServerPort());
                } catch (TelegramApiException e) {
                    log.error("注册 Webhook Bot 失败: {}", e.getMessage(), e);
                    throw new RuntimeException("Bot 注册失败", e);
                }
            });
        } catch (TelegramApiException e) {
            log.error("启动 Webhook Bot 失败: {}", e.getMessage(), e);
            throw new RuntimeException("Bot 启动失败", e);
        }
    }
}
