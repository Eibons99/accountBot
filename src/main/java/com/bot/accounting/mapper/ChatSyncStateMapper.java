package com.bot.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bot.accounting.entity.ChatSyncState;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSyncStateMapper extends BaseMapper<ChatSyncState> {
}
