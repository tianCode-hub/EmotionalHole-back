package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告数据访问层
 * 继承 BaseMapper 获得 insert/updateById/deleteById/selectById 等单表 CRUD 能力
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
