package com.bot.accounting.service;

import com.bot.accounting.entity.User;
import com.bot.accounting.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    
    public User getOrCreateUser(Message message) {
        org.telegram.telegrambots.meta.api.objects.User fromUser = message.getFrom();
        Long telegramId = fromUser.getId();
        
        User user = userMapper.findByTelegramId(telegramId);
        if (user != null) {
            return updateUserInfo(user, fromUser);
        }
        return createUser(fromUser);
    }
    
    public User getOrCreateUser(Long telegramId, String firstName, String lastName, String username) {
        User user = userMapper.findByTelegramId(telegramId);
        if (user != null) {
            return user;
        }
        User newUser = new User();
        newUser.setTelegramId(telegramId);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setUsername(username);
        userMapper.insert(newUser);
        return newUser;
    }
    
    private User updateUserInfo(User user, org.telegram.telegrambots.meta.api.objects.User fromUser) {
        boolean updated = false;
        
        if (!Objects.equals(fromUser.getUserName(), user.getUsername())) {
            user.setUsername(fromUser.getUserName());
            updated = true;
        }
        if (!Objects.equals(fromUser.getFirstName(), user.getFirstName())) {
            user.setFirstName(fromUser.getFirstName());
            updated = true;
        }
        if (!Objects.equals(fromUser.getLastName(), user.getLastName())) {
            user.setLastName(fromUser.getLastName());
            updated = true;
        }
        
        if (updated) {
            userMapper.updateById(user);
        }
        return user;
    }
    
    private User createUser(org.telegram.telegrambots.meta.api.objects.User fromUser) {
        User user = new User();
        user.setTelegramId(fromUser.getId());
        user.setUsername(fromUser.getUserName());
        user.setFirstName(fromUser.getFirstName());
        user.setLastName(fromUser.getLastName());
        userMapper.insert(user);
        return user;
    }
    
    public User findById(Long id) {
        return userMapper.selectById(id);
    }
    
    public User findByTelegramId(Long telegramId) {
        return userMapper.findByTelegramId(telegramId);
    }
    
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
    
    public String getUserDisplayName(User user) {
        if (user == null) {
            return "未知用户";
        }
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) {
            String name = user.getFirstName();
            if (user.getLastName() != null && !user.getLastName().isEmpty()) {
                name += " " + user.getLastName();
            }
            return name;
        }
        return user.getUsername() != null ? user.getUsername() : "用户" + user.getTelegramId();
    }
}
