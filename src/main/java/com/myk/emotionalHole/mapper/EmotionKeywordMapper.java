package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.EmotionKeyword;
import org.apache.ibatis.annotations.Mapper;

/**
 * 情感关键词数据访问层
 * 继承 BaseMapper 获得全部单表 CRUD 能力（CRUD/分页/搜索均改用 Wrapper + Page）
 */
@Mapper
public interface EmotionKeywordMapper extends BaseMapper<EmotionKeyword> {
}
