package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.Topic;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话题数据访问层
 * 继承 BaseMapper 获得全部单表 CRUD 能力（getAllTopics/getTopicByName 等改用 Wrapper）
 */
@Mapper
public interface TopicMapper extends BaseMapper<Topic> {
}
