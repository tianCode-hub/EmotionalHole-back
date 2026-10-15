package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户行为记录Mapper接口
 * 简单单表查询已改用 Wrapper + BaseMapper 内置方法，仅保留复杂 SQL
 */
@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    /**
     * 获取用户最近浏览的内容ID列表（去重，按最近浏览时间排序）
     */
    List<Long> selectRecentViewedContentIds(@Param("anonymousId") String anonymousId, @Param("limit") int limit);

}
