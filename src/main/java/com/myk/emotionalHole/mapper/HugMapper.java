package com.myk.emotionalHole.mapper;

import com.myk.emotionalHole.entity.Content;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.Hug;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface HugMapper extends BaseMapper<Hug> {

    /**
     * 获取用户抱抱的内容列表（联表查询）
     */
    List<Content> getMyHugs(@Param("offset") int offset, @Param("pageSize") int pageSize, @Param("anonymousId") String anonymousId);

    /**
     * 批量获取抱抱数量（group by 统计）
     */
    List<Map<String, Object>> batchGetHugCount(@Param("targetType") int targetType, @Param("targetIds") List<Long> targetIds);

}
