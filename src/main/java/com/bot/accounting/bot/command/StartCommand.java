package com.bot.accounting.bot.command;

import com.bot.accounting.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
@RequiredArgsConstructor
public class StartCommand implements BotCommand {
    
    private final UserService userService;
    
    @Override
    public String execute(Message message) {
        userService.getOrCreateUser(message);
        
        return "👋 欢迎使用记账机器人！\n\n" +
                "我可以帮你轻松记录日常收支。\n\n" +
                "快速开始：\n" +
                "• 直接输入：花了50买咖啡\n" +
                "• 或输入：收入5000工资\n" +
                "• 或使用命令：/add 100 午餐\n\n" +
                "查看帮助：/help";
    }
}
