package com.myk.emotionalHole.mapper;

import com.myk.emotionalHole.entity.Content;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.Like;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LikeMapper extends BaseMapper<Like> {

    /**
     * 获取用户点赞的内容列表（联表查询）
     */
    List<Content> getMyLikes(int offset, int pageSize, String anonymousId);

    /**
     * 批量获取点赞数量（group by 统计）
     */
    List<Map<String, Object>> batchGetLikeCount(int targetType, @Param("targetIds") List<Long> targetIds);

}
