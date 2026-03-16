package com.bot.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bot.accounting.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
    
    @Select("SELECT * FROM system_config WHERE config_key = #{key} AND chat_id = #{chatId} AND deleted = 0 LIMIT 1")
    SystemConfig findByKeyAndChatId(@Param("key") String key, @Param("chatId") Long chatId);
    
    @Select("SELECT * FROM system_config WHERE config_key = #{key} AND chat_id IS NULL AND deleted = 0 LIMIT 1")
    SystemConfig findGlobalByKey(@Param("key") String key);
}
