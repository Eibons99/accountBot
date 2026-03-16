package com.bot.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bot.accounting.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    @Select("SELECT * FROM users WHERE telegram_id = #{telegramId} LIMIT 1")
    User findByTelegramId(@Param("telegramId") Long telegramId);
    
    @Select("SELECT * FROM users WHERE username = #{username} LIMIT 1")
    User findByUsername(@Param("username") String username);
    
    @Select("SELECT * FROM users WHERE is_tagged = false AND is_operator = false ORDER BY id DESC LIMIT 20")
    java.util.List<User> findAvailableUsersForTagged();
    
    @Select("SELECT * FROM users WHERE is_operator = false ORDER BY id DESC LIMIT 20")
    java.util.List<User> findAvailableUsersForOperator();
    
    @Select("SELECT * FROM users ORDER BY id DESC LIMIT 20")
    java.util.List<User> findRecentUsers();
}
