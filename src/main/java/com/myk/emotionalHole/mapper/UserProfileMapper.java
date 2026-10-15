package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户画像Mapper接口
 * 单表查询均改用 Wrapper + BaseMapper 内置方法
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
