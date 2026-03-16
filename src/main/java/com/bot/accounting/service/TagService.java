package com.bot.accounting.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bot.accounting.entity.TaggedRecord;
import com.bot.accounting.entity.User;
import com.bot.accounting.mapper.TaggedRecordMapper;
import com.bot.accounting.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {
    
    private final UserMapper userMapper;
    private final TaggedRecordMapper taggedRecordMapper;
    private final UserService userService;
    private final DayCutoffService dayCutoffService;
    
    private static final Pattern TAG_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d{1,2})?)\\s+(.+)$");
    
    @Transactional
    public User setTaggedUser(Long telegramId, boolean isTagged) {
        User user = userMapper.findByTelegramId(telegramId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setIsTagged(isTagged);
        userMapper.updateById(user);
        return user;
    }
    
    @Transactional
    public User setOperator(Long telegramId, boolean isOperator) {
        User user = userMapper.findByTelegramId(telegramId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setIsOperator(isOperator);
        userMapper.updateById(user);
        return user;
    }
    
    public boolean isTaggedUser(Long telegramId) {
        User user = userMapper.findByTelegramId(telegramId);
        return user != null && Boolean.TRUE.equals(user.getIsTagged());
    }
    
    public boolean isOperator(Long telegramId) {
        User user = userMapper.findByTelegramId(telegramId);
        return user != null && Boolean.TRUE.equals(user.getIsOperator());
    }
    
    public TagParseResult parseTagMessage(String message) {
        Matcher matcher = TAG_PATTERN.matcher(message.trim());
        if (matcher.matches()) {
            try {
                BigDecimal amount = new BigDecimal(matcher.group(1));
                String name = matcher.group(2).trim();
                return TagParseResult.builder()
                        .success(true)
                        .amount(amount)
                        .name(name)
                        .build();
            } catch (NumberFormatException e) {
                log.warn("解析金额失败: {}", message);
            }
        }
        return TagParseResult.builder()
                .success(false)
                .build();
    }
    
    public boolean isTagMessage(String message) {
        return TAG_PATTERN.matcher(message.trim()).matches();
    }
    
    @Transactional
    public TaggedRecord processTagMessage(Message taggedMessage, Message operatorMessage) {
        TagParseResult parseResult = parseTagMessage(taggedMessage.getText());
        if (!parseResult.isSuccess()) {
            throw new RuntimeException("标记消息格式错误");
        }
        
        User taggedUser = userService.getOrCreateUser(taggedMessage);
        User operatorUser = userService.getOrCreateUser(operatorMessage);
        
        TaggedRecord record = new TaggedRecord();
        record.setTaggedUserId(taggedUser.getId());
        record.setOperatorUserId(operatorUser.getId());
        record.setAmount(parseResult.getAmount());
        record.setTagContent(taggedMessage.getText());
        record.setChatId(taggedMessage.getChatId());
        record.setMessageId(taggedMessage.getMessageId());
        
        taggedRecordMapper.insert(record);
        return record;
    }
    
    @Transactional
    public TaggedRecord recordTaggedTransaction(Long taggedTelegramId, Long operatorTelegramId, 
                                                 BigDecimal amount, String tagContent, Long chatId) {
        User taggedUser = userMapper.findByTelegramId(taggedTelegramId);
        User operatorUser = userMapper.findByTelegramId(operatorTelegramId);
        if (taggedUser == null) {
            throw new RuntimeException("标记用户不存在");
        }
        if (operatorUser == null) {
            throw new RuntimeException("操作员不存在");
        }
        
        TaggedRecord record = new TaggedRecord();
        record.setTaggedUserId(taggedUser.getId());
        record.setOperatorUserId(operatorUser.getId());
        record.setAmount(amount);
        record.setTagContent(tagContent);
        record.setChatId(chatId);
        
        taggedRecordMapper.insert(record);
        return record;
    }
    
    public List<TaggedRecord> getTodayRecords(Long chatId) {
        // 使用日切时间计算今日范围
        LocalDateTime startTime = dayCutoffService.getTodayStartTime(chatId);
        LocalDateTime endTime = dayCutoffService.getTodayEndTime(chatId);
        return taggedRecordMapper.findByChatIdAndCreatedAtBetween(chatId, startTime, endTime);
    }
    
    public List<TaggedRecord> getAllRecords(Long chatId) {
        return taggedRecordMapper.findByChatIdOrderByCreatedAtDesc(chatId);
    }
    
    public List<User> getAllTaggedUsers() {
        return userMapper.selectList(new QueryWrapper<User>().eq("is_tagged", true))
                .stream()
                .collect(Collectors.toList());
    }
    
    public List<User> getAllOperators() {
        return userMapper.selectList(new QueryWrapper<User>().eq("is_operator", true))
                .stream()
                .collect(Collectors.toList());
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TagParseResult {
        private boolean success;
        private BigDecimal amount;
        private String name;
        
        public boolean isSuccess() {
            return success;
        }
    }
}
