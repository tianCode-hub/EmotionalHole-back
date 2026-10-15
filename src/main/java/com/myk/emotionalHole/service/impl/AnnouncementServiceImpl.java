package com.myk.emotionalHole.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myk.emotionalHole.entity.Announcement;
import com.myk.emotionalHole.mapper.AnnouncementMapper;
import com.myk.emotionalHole.service.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 公告服务实现类
 * 提供公告的增删改查、置顶、状态管理功能
 * 基于 MyBatis-Plus：insert/updateById/deleteById + LambdaQueryWrapper 分页查询
 */
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean createAnnouncement(Announcement announcement) {
        // 参数校验
        if (announcement.getAdminId() == null) {
            throw new IllegalArgumentException("管理员ID不能为空");
        }
        if (announcement.getIsPinned() == null) {
            throw new IllegalArgumentException("是否置顶不能为空");
        }
        if (announcement.getAnnouncementStatus() == null) {
            throw new IllegalArgumentException("公告状态不能为空");
        }
        // 设置创建时间和更新时间
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        announcement.setCreateTime(now);
        announcement.setUpdateTime(now);
        return announcementMapper.insert(announcement) > 0;
    }

    @Override
    public boolean updateAnnouncement(Announcement announcement) {
        // 更新时间
        announcement.setUpdateTime(LocalDateTime.now().format(TIME_FORMATTER));
        return announcementMapper.updateById(announcement) > 0;
    }

    @Override
    public boolean deleteAnnouncement(Long id) {
        return announcementMapper.deleteById(id) > 0;
    }

    @Override
    public Announcement getAnnouncementById(Long id) {
        return announcementMapper.selectById(id);
    }

    @Override
    public List<Announcement> getAnnouncementList(Integer announcementStatus, Integer announcementType, Integer isPinned, int page, int pageSize) {
        LambdaQueryWrapper<Announcement> wrapper = Wrappers.lambdaQuery();
        if (announcementStatus != null && announcementStatus != -1) {
            wrapper.eq(Announcement::getAnnouncementStatus, announcementStatus);
        }
        if (announcementType != null) {
            wrapper.eq(Announcement::getAnnouncementType, announcementType);
        }
        if (isPinned != null) {
            wrapper.eq(Announcement::getIsPinned, isPinned);
        }
        wrapper.orderByDesc(Announcement::getIsPinned)
               .orderByDesc(Announcement::getCreateTime);

        Page<Announcement> pageResult = announcementMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return pageResult.getRecords();
    }

    @Override
    public boolean updatePinnedStatus(Long id, Integer isPinned) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            return false;
        }
        announcement.setIsPinned(isPinned);
        announcement.setUpdateTime(LocalDateTime.now().format(TIME_FORMATTER));
        return announcementMapper.updateById(announcement) > 0;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            return false;
        }
        announcement.setAnnouncementStatus(status);
        announcement.setUpdateTime(LocalDateTime.now().format(TIME_FORMATTER));
        return announcementMapper.updateById(announcement) > 0;
    }
}
