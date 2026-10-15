package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 私信消息数据访问层
 * 继承 BaseMapper 获得 insert/deleteById/selectById 等单表 CRUD 能力
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 获取与特定用户的对话消息列表
     */
    List<Message> getConversation(
            @Param("anonymousId") String anonymousId,
            @Param("targetAnonymousId") String targetAnonymousId,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    /**
     * 获取用户的对话列表（最近联系人）
     */
    List<Message> getConversations(
            @Param("anonymousId") String anonymousId,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);
}
