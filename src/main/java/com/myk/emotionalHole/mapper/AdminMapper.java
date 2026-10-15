package com.myk.emotionalHole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myk.emotionalHole.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员数据访问层
 * 继承 BaseMapper 获得全部单表 CRUD 能力（getAdminByAccount/getAdminById 等改用 Wrapper）
 */
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {
}
