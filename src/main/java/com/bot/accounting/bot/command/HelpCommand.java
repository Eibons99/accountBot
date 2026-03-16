package com.bot.accounting.bot.command;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
public class HelpCommand implements BotCommand {
    
    @Override
    public String execute(Message message) {
        return "📖 <b>记账机器人使用指南</b>\n\n" +
                "<b>📝 快速记账：</b>\n" +
                "• 100 早餐 - 个人记账（操作员）\n" +
                "• @张三 100 午餐 - 群组记账（标记员）\n" +
                "• 直接发送金额自动识别\n\n" +
                "<b>👥 人员管理：</b>\n" +
                "• /标记员列表 - 查看所有标记员\n" +
                "• /操作员列表 - 查看所有操作员\n" +
                "• 设置标记员@用户名 - 设置标记人员\n" +
                "• 设置操作员@用户名 - 设置操作人员\n\n" +
                "<b>📊 查询统计：</b>\n" +
                "• /today - 今日统计\n" +
                "• /history - 历史记录\n" +
                "• /member @成员 - 成员账单\n" +
                "• /list - 最近 10 条记录\n\n" +
                "<b>⚙️ 常用命令：</b>\n" +
                "• /start - 启动机器人\n" +
                "• /help - 显示此帮助\n" +
                "• /delete [ID] - 删除记录\n\n" +
                "<b>💡 提示：</b>\n" +
                "• 群主和管理员可设置人员权限\n" +
                "• 标记员可为他人记账\n" +
                "• 操作员可直接记账并确认出入账";
    }
}
