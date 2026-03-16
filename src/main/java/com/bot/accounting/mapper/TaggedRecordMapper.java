package com.bot.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bot.accounting.entity.TaggedRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaggedRecordMapper extends BaseMapper<TaggedRecord> {
    
    @Select("SELECT * FROM tagged_records WHERE tagged_user_id = #{taggedUserId} ORDER BY created_at DESC")
    List<TaggedRecord> findByTaggedUserIdOrderByCreatedAtDesc(@Param("taggedUserId") Long taggedUserId);
    
    @Select("SELECT * FROM tagged_records WHERE operator_user_id = #{operatorUserId} ORDER BY created_at DESC")
    List<TaggedRecord> findByOperatorUserIdOrderByCreatedAtDesc(@Param("operatorUserId") Long operatorUserId);
    
    @Select("SELECT * FROM tagged_records WHERE chat_id = #{chatId} ORDER BY created_at DESC")
    List<TaggedRecord> findByChatIdOrderByCreatedAtDesc(@Param("chatId") Long chatId);
    
    @Select("SELECT * FROM tagged_records WHERE created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<TaggedRecord> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    @Select("SELECT * FROM tagged_records WHERE chat_id = #{chatId} " +
            "AND created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<TaggedRecord> findByChatIdAndCreatedAtBetween(@Param("chatId") Long chatId, 
                                                        @Param("startTime") LocalDateTime startTime, 
                                                        @Param("endTime") LocalDateTime endTime);
    
    @Select("SELECT COALESCE(SUM(amount), 0) FROM tagged_records " +
            "WHERE operator_user_id = #{operatorUserId}")
    BigDecimal sumAmountByOperatorUserId(@Param("operatorUserId") Long operatorUserId);
    
    @Select("SELECT COALESCE(SUM(amount), 0) FROM tagged_records WHERE chat_id = #{chatId}")
    BigDecimal sumAmountByChatId(@Param("chatId") Long chatId);
}
