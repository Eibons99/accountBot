package com.bot.accounting.bot;

import com.bot.accounting.bot.command.AddCommand;
import com.bot.accounting.bot.command.BotCommand;
import com.bot.accounting.bot.command.CategoryCommand;
import com.bot.accounting.bot.command.DeleteCommand;
import com.bot.accounting.bot.command.ExportCommand;
import com.bot.accounting.bot.command.HelpCommand;
import com.bot.accounting.bot.command.IncomeCommand;
import com.bot.accounting.bot.command.ListCommand;
import com.bot.accounting.bot.command.MonthCommand;
import com.bot.accounting.bot.command.StartCommand;
import com.bot.accounting.bot.command.TagCommand;
import com.bot.accounting.bot.command.TodayCommand;
import com.bot.accounting.entity.User;
import com.bot.accounting.service.AdminService;
import com.bot.accounting.service.DayCutoffService;
import com.bot.accounting.service.GroupPermissionService;
import com.bot.accounting.service.PendingMessageService;
import com.bot.accounting.service.TagService;
import com.bot.accounting.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandDispatcher {
    
    private final StartCommand startCommand;
    private final HelpCommand helpCommand;
    private final AddCommand addCommand;
    private final IncomeCommand incomeCommand;
    private final ListCommand listCommand;
    private final TodayCommand todayCommand;
    private final MonthCommand monthCommand;
    private final CategoryCommand categoryCommand;
    private final DeleteCommand deleteCommand;
    private final TagCommand tagCommand;
    private final ExportCommand exportCommand;  // 添加导出命令
    private final TagService tagService;
    private final DayCutoffService dayCutoffService;
    private final AdminService adminService;
    private final UserService userService;
    private final GroupPermissionService groupPermissionService;
    private final PendingMessageService pendingMessageService;
    private final CallbackHandler callbackHandler;
    
    private final Map<String, BotCommand> commandMap = new HashMap<>();
    
    // 匹配 +金额 格式（操作员回复格式）
    private static final Pattern OPERATOR_REPLY_PATTERN = Pattern.compile("^\\+(\\d+(?:\\.\\d{1,2})?)$");
    
    // 匹配 -金额 格式（操作员直接扣款）
    private static final Pattern OPERATOR_EXPENSE_PATTERN = Pattern.compile("^-(\\d+(?:\\.\\d{1,2})?)$");
    
    // 匹配 设置日切X 格式
    private static final Pattern DAY_CUTOFF_PATTERN = Pattern.compile("^设置日切(\\d+)$");
    
    @javax.annotation.PostConstruct
    public void init() {
        commandMap.put("/start", startCommand);
        commandMap.put("/help", helpCommand);
        commandMap.put("/add", addCommand);
        commandMap.put("/income", incomeCommand);
        commandMap.put("/list", listCommand);
        commandMap.put("/today", todayCommand);
        commandMap.put("/month", monthCommand);
        commandMap.put("/category", categoryCommand);
        commandMap.put("/delete", deleteCommand);
        commandMap.put("/tag", tagCommand);
        commandMap.put("/export", exportCommand);  // 注册导出命令
    }
    
    public Object dispatch(Message message) {
        String text = message.getText();
        String command = extractCommand(text);
        
        BotCommand botCommand = commandMap.get(command);
        if (botCommand != null) {
            // 特殊处理 TodayCommand，返回 SendMessage 对象
            if (botCommand instanceof TodayCommand) {
                return ((TodayCommand) botCommand).executeWithKeyboard(message);
            }
            return botCommand.execute(message);
        }
        
        // 如果不是命令，检查是否是操作员回复的 +金额 格式
        if (!text.startsWith("/")) {
            // 检查是否是中文设置指令：设置标记人@成员 或 设置操作人@成员
            String chineseCommandResult = handleChineseCommand(message);
            if (chineseCommandResult != null) {
                return chineseCommandResult;
            }
            
            // 检查是否是操作员发送的 +金额 或 -金额 消息
            String operatorResult = handleOperatorMessage(message);
            if (operatorResult != null) {
                return operatorResult;
            }
            
            // 检查是否是标记人员发送的标记消息
            if (tagService.isTagMessage(text)) {
                // 标记人员发送的消息，等待操作员回复
                return handleTagMessage(message);
            }
            
            // 普通自然语言解析
            return addCommand.executeNatural(message);
        }
        
        return "未知命令。使用 /help 查看可用命令。";
    }
    
    /**
     * 分发并返回内联键盘消息（用于设置标记员/操作员等需要选择的操作）
     * 返回 null 表示不需要内联键盘
     */
    public SendMessage dispatchWithKeyboard(Message message) {
        String text = message.getText().trim();
        
        // 检查权限：只有群主和管理员可以设置
        if (!groupPermissionService.isGroupAdmin(message)) {
            return null;
        }
        
        // 设置标记员（无参数）
        if (text.equals("设置标记员") || text.equals("设置标记人")) {
            return callbackHandler.createSetTaggedKeyboard(message.getChatId());
        }
        
        // 设置操作员（无参数）
        if (text.equals("设置操作员") || text.equals("设置操作人")) {
            return callbackHandler.createSetOperatorKeyboard(message.getChatId());
        }
        
        // 取消标记员（无参数）
        if (text.equals("取消标记员") || text.equals("取消标记人") ||
            text.equals("删除标记员") || text.equals("删除标记人") ||
            text.equals("移除标记员") || text.equals("移除标记人")) {
            return callbackHandler.createRemoveTaggedKeyboard(message.getChatId());
        }
        
        // 取消操作员（无参数）
        if (text.equals("取消操作员") || text.equals("取消操作人") ||
            text.equals("删除操作员") || text.equals("删除操作人") ||
            text.equals("移除操作员") || text.equals("移除操作人")) {
            return callbackHandler.createRemoveOperatorKeyboard(message.getChatId());
        }
        
        return null;
    }
    
    /**
     * 处理中文指令：设置标记员 @成员 或 设置操作员 @成员
     */
    private String handleChineseCommand(Message message) {
        String text = message.getText().trim();
        
        // 匹配 "设置日切X" 格式
        Matcher dayCutoffMatcher = DAY_CUTOFF_PATTERN.matcher(text);
        if (dayCutoffMatcher.matches()) {
            return handleDayCutoffCommand(message, dayCutoffMatcher.group(1));
        }
        
        // 匹配 "设置标记员 @成员" 格式（必须有空格）
        if (text.startsWith("设置标记员 ") || text.startsWith("设置标记人 ")) {
            return handleChineseSetTaggedUser(message, text);
        }
        
        // 匹配 "设置操作员 @成员" 格式（必须有空格）
        if (text.startsWith("设置操作员 ") || text.startsWith("设置操作人 ")) {
            return handleChineseSetOperator(message, text);
        }

        // 匹配 "取消标记员 @成员" 格式
        if (text.startsWith("取消标记员 ") || text.startsWith("取消标记人 ") ||
            text.startsWith("删除标记员 ") || text.startsWith("删除标记人 ") ||
            text.startsWith("移除标记员 ") || text.startsWith("移除标记人 ")) {
            return handleRemoveTaggedUser(message, text);
        }

        // 匹配 "取消操作员 @成员" 格式
        if (text.startsWith("取消操作员 ") || text.startsWith("取消操作人 ") ||
            text.startsWith("删除操作员 ") || text.startsWith("删除操作人 ") ||
            text.startsWith("移除操作员 ") || text.startsWith("移除操作人 ")) {
            return handleRemoveOperator(message, text);
        }

        return null;
    }
    
    /**
     * 处理日切时间设置指令
     */
    private String handleDayCutoffCommand(Message message, String hourStr) {
        // 检查权限：群主、管理员、标记员、操作员都可以设置
        if (!canSetDayCutoff(message)) {
            return "权限不足，只有群主、管理员、标记员或操作员才能设置日切时间";
        }
        
        try {
            int hour = Integer.parseInt(hourStr);
            if (hour < 0 || hour > 23) {
                return "日切时间小时数必须在 0-23 之间";
            }
            
            dayCutoffService.setDayCutoffTime(message.getChatId(), hour, 0);
            return String.format("日切时间已设置为 %02d:00，账目将从每天 %02d:00 开始记录新的一天", hour, hour);
        } catch (NumberFormatException e) {
            return "格式错误，请使用：设置日切X（X为0-23的数字）";
        }
    }
    
    /**
     * 检查用户是否有权限设置日切时间
     * 群主、群组管理员、配置的管理员拥有所有权限
     */
    private boolean canSetDayCutoff(Message message) {
        Long userId = message.getFrom().getId();
        
        // 群主拥有所有权限
        if (groupPermissionService.isGroupCreator(message)) {
            return true;
        }
        // 群组管理员拥有所有权限
        if (groupPermissionService.isGroupAdmin(message)) {
            return true;
        }
        // 配置的管理员拥有所有权限
        if (adminService.isAdmin(userId)) {
            return true;
        }
        // 标记员
        if (tagService.isTaggedUser(userId)) {
            return true;
        }
        // 操作员
        if (tagService.isOperator(userId)) {
            return true;
        }
        return false;
    }
    
    /**
     * 检查用户是否是操作员（包括群主、群组管理员和配置的管理员）
     */
    private boolean isOperatorOrAdmin(Message message) {
        Long userId = message.getFrom().getId();
        
        // 群主拥有所有权限
        if (groupPermissionService.isGroupCreator(message)) {
            return true;
        }
        // 群组管理员拥有所有权限
        if (groupPermissionService.isGroupAdmin(message)) {
            return true;
        }
        // 配置的管理员拥有所有权限
        if (adminService.isAdmin(userId)) {
            return true;
        }
        // 操作员
        return tagService.isOperator(userId);
    }
    
    /**
     * 处理中文指令：设置标记人
     */
    private String handleChineseSetTaggedUser(Message message, String text) {
        // 严格权限控制：只有群主和群组管理员可以设置标记人员
        if (!groupPermissionService.isGroupAdmin(message)) {
            return "❌ 权限不足\n只有群主和群组管理员才能设置标记人员";
        }

        // 检查是否是取消设置
        if (text.contains("取消") || text.contains("删除") || text.contains("移除")) {
            return handleRemoveTaggedUser(message, text);
        }
        
        // 提取 @用户名
        String username = extractMentionUsername(text);
        if (username == null) {
            return "❌ 格式错误\n\n正确格式：\n设置标记员 @用户名\n或：设置标记人 @用户名\n\n取消设置：\n取消标记员 @用户名";
        }
        
        // 从消息实体中获取用户信息
        Long telegramId = extractUserIdFromMention(message, username);
        if (telegramId == null) {
            return "❌ 无法识别用户\n\n请确保：\n1. 正确使用 @ 提及用户\n2. 用户在群组中\n3. 用户已发送过消息";
        }
        
        try {
            userService.getOrCreateUser(telegramId, username, null, username);
            User user = tagService.setTaggedUser(telegramId, true);

            return String.format("✅ 设置成功\n\n标记员：%s\n权限：可以发送报账消息\n报账格式：'金额 备注'\n\n查看所有标记员：/tag list",
                    userService.getUserDisplayName(user));
        } catch (Exception e) {
            log.error("设置标记员失败", e);
            return "❌ 设置失败：" + e.getMessage();
        }
    }
    
    /**
     * 处理中文指令：设置操作人
     */
    private String handleChineseSetOperator(Message message, String text) {
        // 严格权限控制：只有群主和群组管理员可以设置操作员
        if (!groupPermissionService.isGroupAdmin(message)) {
            return "❌ 权限不足\n只有群主和群组管理员才能设置操作员";
        }

        // 检查是否是取消设置
        if (text.contains("取消") || text.contains("删除") || text.contains("移除")) {
            return handleRemoveOperator(message, text);
        }
        
        // 提取 @用户名
        String username = extractMentionUsername(text);
        if (username == null) {
            return "❌ 格式错误\n\n正确格式：\n设置操作员 @用户名\n或：设置操作人 @用户名\n\n取消设置：\n取消操作员 @用户名";
        }
        
        // 从消息实体中获取用户信息
        Long telegramId = extractUserIdFromMention(message, username);
        if (telegramId == null) {
            return "❌ 无法识别用户\n\n请确保：\n1. 正确使用 @ 提及用户\n2. 用户在群组中\n3. 用户已发送过消息";
        }
        
        try {
            userService.getOrCreateUser(telegramId, username, null, username);
            User user = tagService.setOperator(telegramId, true);

            return String.format("✅ 设置成功\n\n操作员：%s\n权限：可以确认入账/出账\n\n查看所有操作员：/tag operator",
                    userService.getUserDisplayName(user));
        } catch (Exception e) {
            log.error("设置操作员失败", e);
            return "❌ 设置失败：" + e.getMessage();
        }
    }
    
    /**
     * 处理移除标记员
     */
    private String handleRemoveTaggedUser(Message message, String text) {
        // 严格权限控制：只有群主和群组管理员可以移除标记人员
        if (!groupPermissionService.isGroupAdmin(message)) {
            return "❌ 权限不足\n只有群主和群组管理员才能移除标记人员";
        }

        // 提取 @用户名
        String username = extractMentionUsername(text);
        if (username == null) {
            return "❌ 格式错误\n\n正确格式：\n取消标记员 @用户名\n或：取消标记人 @用户名";
        }

        // 从消息实体中获取用户信息
        Long telegramId = extractUserIdFromMention(message, username);
        if (telegramId == null) {
            return "❌ 无法识别用户\n\n请确保：\n1. 正确使用 @ 提及用户\n2. 用户在群组中\n3. 用户已发送过消息";
        }

        try {
            User user = tagService.setTaggedUser(telegramId, false);
            return String.format("✅ 移除成功\n\n标记员：%s\n已取消标记员权限\n\n查看所有标记员：/tag list",
                    userService.getUserDisplayName(user));
        } catch (Exception e) {
            log.error("移除标记员失败", e);
            return "❌ 移除失败：" + e.getMessage();
        }
    }

    /**
     * 处理移除操作员
     */
    private String handleRemoveOperator(Message message, String text) {
        // 严格权限控制：只有群主和群组管理员可以移除操作员
        if (!groupPermissionService.isGroupAdmin(message)) {
            return "❌ 权限不足\n只有群主和群组管理员才能移除操作员";
        }

        // 提取 @用户名
        String username = extractMentionUsername(text);
        if (username == null) {
            return "❌ 格式错误\n\n正确格式：\n取消操作员 @用户名\n或：取消操作人 @用户名";
        }

        // 从消息实体中获取用户信息
        Long telegramId = extractUserIdFromMention(message, username);
        if (telegramId == null) {
            return "❌ 无法识别用户\n\n请确保：\n1. 正确使用 @ 提及用户\n2. 用户在群组中\n3. 用户已发送过消息";
        }

        try {
            User user = tagService.setOperator(telegramId, false);
            return String.format("✅ 移除成功\n\n操作员：%s\n已取消操作员权限\n\n查看所有操作员：/tag operator",
                    userService.getUserDisplayName(user));
        } catch (Exception e) {
            log.error("移除操作员失败", e);
            return "❌ 移除失败：" + e.getMessage();
        }
    }

    /**
     * 从文本中提取 @用户名
     */
    private String extractMentionUsername(String text) {
        // 移除指令部分，提取 @用户名
        int atIndex = text.indexOf('@');
        if (atIndex == -1) {
            return null;
        }
        
        // 提取 @ 后面的用户名
        String afterAt = text.substring(atIndex + 1).trim();
        // 如果后面有空格，只取第一个词
        int spaceIndex = afterAt.indexOf(' ');
        if (spaceIndex > 0) {
            afterAt = afterAt.substring(0, spaceIndex);
        }
        return afterAt;
    }
    
    /**
     * 从消息实体中获取被@用户的ID
     * 注意：Telegram 的 mention 实体只提供用户名，不提供用户ID
     * 需要通过其他方式（如用户数据库）来映射用户名到用户ID
     */
    private Long extractUserIdFromMention(Message message, String username) {
        // 优先从消息实体中查找 text_mention 类型（这种类型包含用户ID）
        if (message.getEntities() != null) {
            for (MessageEntity entity : message.getEntities()) {
                // text_mention 类型包含完整的用户信息（包括ID）
                if ("text_mention".equals(entity.getType())) {
                    if (entity.getUser() != null) {
                        String mentionText = entity.getText();
                        if (mentionText != null && mentionText.contains(username)) {
                            return entity.getUser().getId();
                        }
                    }
                }
            }
        }
        
        // 尝试通过用户名从数据库查找用户
        User user = userService.findByUsername(username);
        if (user != null) {
            return user.getTelegramId();
        }
        
        // 尝试将用户名作为数字解析（直接@用户ID的情况）
        try {
            return Long.parseLong(username);
        } catch (NumberFormatException e) {
            // 无法识别用户ID
            return null;
        }
    }
    
    /**
     * 处理操作员发送的消息（+金额 或 -金额）
     * 返回非null表示已处理，返回null表示不是操作员消息
     * 群主和管理员也拥有操作员权限
     */
    private String handleOperatorMessage(Message message) {
        String text = message.getText().trim();
        
        // 检查发送者是否是操作员、群主或管理员（群主和管理员拥有所有权限）
        if (!isOperatorOrAdmin(message)) {
            return null;
        }
        
        // 解析操作员输入的金额
        java.math.BigDecimal amount = null;
        boolean isIncome = true;
        
        Matcher plusMatcher = OPERATOR_REPLY_PATTERN.matcher(text);
        if (plusMatcher.matches()) {
            amount = new java.math.BigDecimal(plusMatcher.group(1));
            isIncome = true;
        } else {
            Matcher minusMatcher = OPERATOR_EXPENSE_PATTERN.matcher(text);
            if (minusMatcher.matches()) {
                amount = new java.math.BigDecimal(minusMatcher.group(1));
                isIncome = false;
            }
        }
        
        if (amount == null) {
            return null; // 不是操作员消息格式
        }
        
        // 检查是否是回复标记员消息
        if (message.isReply() && message.getReplyToMessage() != null) {
            Message repliedMessage = message.getReplyToMessage();
            String repliedText = repliedMessage.getText();
            Integer repliedMessageId = repliedMessage.getMessageId();
            
            // 先从 Redis 查找缓存的标记消息（服务器重启后可能还在）
            PendingMessageService.PendingTagMessage cachedTagMessage = 
                    pendingMessageService.getAndRemoveTaggedMessage(message.getChatId(), repliedMessageId);
            
            if (cachedTagMessage != null) {
                // 找到缓存的标记消息，使用缓存的信息
                log.info("从Redis找到缓存的标记消息: chatId={}, messageId={}", 
                        message.getChatId(), repliedMessageId);
                return processOperatorReplyWithCachedTag(message, cachedTagMessage, amount, isIncome);
            }
            
            // 检查被回复的消息是否是标记消息格式
            if (tagService.isTagMessage(repliedText)) {
                // 解析标记消息
                TagService.TagParseResult result = tagService.parseTagMessage(repliedText);
                if (result.isSuccess()) {
                    // 操作员回复 +金额，记录入账
                    return processOperatorReply(message, repliedMessage, amount, isIncome);
                }
            }
        }
        
        // 直接输入 +金额 或 -金额，无标记员
        return processOperatorDirect(message, amount, isIncome);
    }
    
    /**
     * 处理操作员回复标记员消息（使用缓存的标记消息）
     * 服务器重启后，从Redis获取缓存的标记消息
     */
    private String processOperatorReplyWithCachedTag(Message operatorMessage,
                                                      PendingMessageService.PendingTagMessage cachedTagMessage,
                                                      java.math.BigDecimal amount, boolean isIncome) {
        // 第1步：先缓存操作员消息到Redis
        try {
            User operatorUser = userService.getOrCreateUser(operatorMessage);
            pendingMessageService.cacheOperatorMessage(
                    operatorMessage.getChatId(),
                    operatorMessage.getMessageId(),
                    operatorUser.getTelegramId(),
                    operatorMessage.getText(),
                    userService.getUserDisplayName(operatorUser),
                    cachedTagMessage.getMessageId()
            );
        } catch (Exception cacheError) {
            log.error("预缓存操作员消息失败", cacheError);
            return "系统错误，请稍后重试";
        }
        
        // 第2步：处理数据库
        try {
            User operatorUser = userService.getOrCreateUser(operatorMessage);
            
            // 获取或创建标记员用户
            User taggedUser = userService.findByTelegramId(cachedTagMessage.getTaggedUserId());
            if (taggedUser == null) {
                taggedUser = userService.getOrCreateUser(
                        cachedTagMessage.getTaggedUserId(),
                        cachedTagMessage.getTaggedUserName(),
                        null, null);
            }

            // 解析标记消息中的备注（格式：金额 备注）
            String remark = extractRemarkFromTagMessage(cachedTagMessage.getMessageText());

            // 记录标记记录
            tagService.recordTaggedTransaction(
                    cachedTagMessage.getTaggedUserId(),
                    operatorUser.getTelegramId(),
                    amount,
                    cachedTagMessage.getMessageText(),
                    operatorMessage.getChatId()
            );
            
            // 记录到交易表
            addCommand.recordTransaction(operatorMessage, amount,
                    isIncome ? "入账" : "出账",
                    remark,
                    taggedUser.getId(),
                    operatorUser.getId());
            
            // 第3步：成功后移除缓存
            pendingMessageService.removePendingOperatorMessage(
                    operatorMessage.getChatId(), operatorMessage.getMessageId());

            // 返回今日统计
            return todayCommand.execute(operatorMessage);
        } catch (Exception e) {
            log.error("处理操作员回复失败，消息已缓存到Redis等待恢复", e);
            return "记录失败，消息已缓存，系统恢复后将自动处理";
        }
    }
    
    /**
     * 处理操作员回复标记员消息
     * 先缓存到Redis，再处理数据库，确保消息不丢失
     */
    private String processOperatorReply(Message operatorMessage, Message taggedMessage,
                                         java.math.BigDecimal amount, boolean isIncome) {
        // 第1步：先缓存到Redis（确保消息不会丢失）
        try {
            User operatorUser = userService.getOrCreateUser(operatorMessage);
            pendingMessageService.cacheOperatorMessage(
                    operatorMessage.getChatId(),
                    operatorMessage.getMessageId(),
                    operatorUser.getTelegramId(),
                    operatorMessage.getText(),
                    userService.getUserDisplayName(operatorUser),
                    taggedMessage.getMessageId()
            );
            log.debug("操作员消息已预缓存到Redis: chatId={}, messageId={}",
                    operatorMessage.getChatId(), operatorMessage.getMessageId());
        } catch (Exception cacheError) {
            log.error("预缓存操作员消息失败", cacheError);
            return "系统错误，请稍后重试";
        }
        
        // 第2步：处理数据库
        try {
            User taggedUser = userService.getOrCreateUser(taggedMessage);
            User operatorUser = userService.getOrCreateUser(operatorMessage);

            // 解析标记消息中的备注（格式：金额 备注）
            String remark = extractRemarkFromTagMessage(taggedMessage.getText());

            // 记录标记记录（包含标记员和操作员信息）
            tagService.recordTaggedTransaction(
                    taggedUser.getTelegramId(),
                    operatorUser.getTelegramId(),
                    amount,
                    taggedMessage.getText(),
                    operatorMessage.getChatId()
            );
            
            // 同时记录到交易表
            addCommand.recordTransaction(operatorMessage, amount,
                    isIncome ? "入账" : "出账",
                    remark,
                    taggedUser.getId(),
                    operatorUser.getId());
            
            // 第3步：成功后移除缓存
            pendingMessageService.getAndRemoveTaggedMessage(
                    taggedMessage.getChatId(), taggedMessage.getMessageId());
            pendingMessageService.removePendingOperatorMessage(
                    operatorMessage.getChatId(), operatorMessage.getMessageId());

            // 返回今日统计
            return todayCommand.execute(operatorMessage);
        } catch (Exception e) {
            log.error("处理操作员回复失败，消息已缓存到Redis等待恢复", e);
            return "记录失败，消息已缓存，系统恢复后将自动处理";
        }
    }
    
    /**
     * 处理操作员直接输入金额（无标记员）
     * 先缓存到Redis，再处理数据库，确保消息不丢失
     */
    private String processOperatorDirect(Message message, java.math.BigDecimal amount, boolean isIncome) {
        // 第1步：先缓存到Redis（确保消息不会丢失）
        try {
            User operatorUser = userService.getOrCreateUser(message);
            pendingMessageService.cacheOperatorMessage(
                    message.getChatId(),
                    message.getMessageId(),
                    operatorUser.getTelegramId(),
                    message.getText(),
                    userService.getUserDisplayName(operatorUser),
                    null
            );
            log.debug("操作员消息已预缓存到Redis: chatId={}, messageId={}",
                    message.getChatId(), message.getMessageId());
        } catch (Exception cacheError) {
            log.error("预缓存操作员消息失败", cacheError);
            return "系统错误，请稍后重试";
        }
        
        // 第2步：处理数据库
        try {
            User operatorUser = userService.getOrCreateUser(message);
            
            // 记录到交易表，标记员为空
            addCommand.recordTransaction(message, amount,
                    isIncome ? "直接入账" : "直接出账",
                    "无备注（无标记员）");

            // 第3步：成功后移除缓存
            pendingMessageService.removePendingOperatorMessage(
                    message.getChatId(), message.getMessageId());

            // 返回今日统计
            return todayCommand.execute(message);
        } catch (Exception e) {
            log.error("处理操作员直接输入失败，消息已缓存到Redis等待恢复", e);
            return "记录失败，消息已缓存，系统恢复后将自动处理";
        }
    }
    
    /**
     * 处理标记人员发送的消息
     */
    private String handleTagMessage(Message message) {
        // 缓存标记消息到 Redis，防止机器人中断后丢失
        try {
            User taggedUser = userService.getOrCreateUser(message);
            pendingMessageService.cacheTaggedMessage(
                    message.getChatId(),
                    message.getMessageId(),
                    taggedUser.getTelegramId(),
                    message.getText(),
                    userService.getUserDisplayName(taggedUser)
            );
            log.debug("标记消息已缓存到Redis: chatId={}, messageId={}", 
                    message.getChatId(), message.getMessageId());
        } catch (Exception e) {
            log.error("缓存标记消息失败", e);
        }
        
        // 不向群里发送回复消息，静默处理
        return null;
    }
    
    /**
     * 从标记消息中提取备注
     * 标记消息格式：金额 备注
     * 例如："113 吴琼芝" -> "吴琼芝"
     */
    private String extractRemarkFromTagMessage(String tagMessage) {
        if (tagMessage == null || tagMessage.trim().isEmpty()) {
            return "无备注";
        }

        // 使用 TagService 的解析方法
        TagService.TagParseResult result = tagService.parseTagMessage(tagMessage);
        if (result.isSuccess() && result.getName() != null && !result.getName().isEmpty()) {
            return result.getName();
        }

        // 如果解析失败，尝试简单分割
        String[] parts = tagMessage.trim().split("\\s+", 2);
        if (parts.length >= 2) {
            return parts[1];
        }

        return "无备注";
    }

    private String extractCommand(String text) {
        if (!text.startsWith("/")) {
            return "";
        }
        
        int spaceIndex = text.indexOf(' ');
        if (spaceIndex > 0) {
            return text.substring(0, spaceIndex);
        }
        return text;
    }
}
