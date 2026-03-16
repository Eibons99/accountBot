package com.bot.accounting.bot.command;

import com.bot.accounting.entity.User;
import com.bot.accounting.service.TagService;
import com.bot.accounting.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagCommand implements BotCommand {

    private final TagService tagService;
    private final UserService userService;

    @Override
    public String execute(Message message) {
        String text = message.getText().trim();
        
        // 支持中文指令格式
        if (text.equals("/tag")) {
            return showTagHelp();
        }
        
        // 新的中文指令格式
        if (text.equals("/标记员列表") || text.equals("/查看标记员")) {
            return listTaggedUsers();
        } else if (text.equals("/操作员列表") || text.equals("/查看操作员")) {
            return listOperators();
        }
        
        return showTagHelp();
    }

    /**
     * 显示帮助信息
     */
    private String showTagHelp() {
        return "📋 人员管理命令：\n\n" +
               "查看列表：\n" +
               "  /标记员列表 - 列出所有标记人员\n" +
               "  /操作员列表 - 列出所有操作员\n" +
               "\n设置权限（需管理员权限）：\n" +
               "  设置标记人@用户名 - 设置标记人员\n" +
               "  设置操作人@用户名 - 设置操作员\n" +
               "\n取消权限：\n" +
               "  取消标记人@用户名 - 取消标记人员\n" +
               "  取消操作人@用户名 - 取消操作员";
    }

    /**
     * 列出所有标记人员
     */
    private String listTaggedUsers() {
        List<User> taggedUsers = tagService.getAllTaggedUsers();
        if (taggedUsers.isEmpty()) {
            return "暂无标记人员";
        }

        String userList = taggedUsers.stream()
                .map(userService::getUserDisplayName)
                .collect(Collectors.joining("\n"));

        return "标记人员列表（" + taggedUsers.size() + "人）：\n" + userList;
    }

    /**
     * 列出所有操作员
     */
    private String listOperators() {
        List<User> operators = tagService.getAllOperators();
        if (operators.isEmpty()) {
            return "暂无操作员";
        }

        String userList = operators.stream()
                .map(userService::getUserDisplayName)
                .collect(Collectors.joining("\n"));

        return "操作员列表（" + operators.size() + "人）：\n" + userList;
    }
}
