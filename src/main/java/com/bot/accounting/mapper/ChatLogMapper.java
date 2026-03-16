package com.bot.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bot.accounting.entity.ChatLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatLogMapper extends BaseMapper<ChatLog> {
    
    @Select("SELECT * FROM chat_logs WHERE chat_id = #{chatId} ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatLog> findRecentByChatId(@Param("chatId") Long chatId, @Param("limit") int limit);
    
    @Select("SELECT * FROM chat_logs WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatLog> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    
    @Select("SELECT * FROM chat_logs WHERE created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<ChatLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    @Select("SELECT * FROM chat_logs WHERE chat_id = #{chatId} AND created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<ChatLog> findByChatIdAndTimeRange(@Param("chatId") Long chatId, 
                                            @Param("startTime") LocalDateTime startTime, 
                                            @Param("endTime") LocalDateTime endTime);
}
