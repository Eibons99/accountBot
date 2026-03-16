package com.bot.accounting.bot.command;

import org.telegram.telegrambots.meta.api.objects.Message;

public interface BotCommand {
    String execute(Message message);
}
