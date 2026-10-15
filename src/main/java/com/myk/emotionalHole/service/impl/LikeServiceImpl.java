package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.common.Result;
import com.myk.emotionalHole.entity.Content;
import com.myk.emotionalHole.entity.Like;
import com.myk.emotionalHole.mapper.ContentMapper;
import com.myk.emotionalHole.mapper.LikeMapper;
import com.myk.emotionalHole.service.CommentService;
import com.myk.emotionalHole.service.LikeService;
import com.myk.emotionalHole.service.RecommendationService;
import com.myk.emotionalHole.util.ExceptionUtils;
import com.myk.emotionalHole.util.RedisCacheService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点赞服务实现类
 * 提供点赞/取消、状态查询、批量查询功能
 * 支持内容点赞和评论点赞两种类型
 */
@Service
public class LikeServiceImpl implements LikeService {

    @Resource
    private LikeMapper likeMapper;

    @Resource
    private ContentMapper contentMapper;

    @Resource
    private CommentService commentService;

    @Resource
    private RecommendationService recommendationService;

    @Resource
    private RedisCacheService redisCacheService;

    /** 点赞：防重复 + 原子更新计数 + 记录用户行为 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addLike(Like like) {
        // 参数校验
        ExceptionUtils.checkNotNull(like, "点赞信息不能为空");
        ExceptionUtils.checkNotNull(like.getTargetType(), "目标类型不能为空");
        ExceptionUtils.checkNotNull(like.getTargetId(), "目标ID不能为空");
        ExceptionUtils.checkNotNull(like.getAnonymousId(), "用户标识不能为空");
        ExceptionUtils.checkCondition(!like.getAnonymousId().trim().isEmpty(), "用户标识不能为空");

        // 检查是否已经点赞
        boolean alreadyLiked = likeMapper.selectCount(
                Wrappers.<Like>lambdaQuery()
                        .eq(Like::getTargetType, like.getTargetType())
                        .eq(Like::getTargetId, like.getTargetId())
                        .eq(Like::getAnonymousId, like.getAnonymousId())) > 0;
        if (alreadyLiked) {
            return Result.error(400, "已经点过赞了");
        }

        // 设置默认值
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        like.setCreateTime(LocalDateTime.now().format(formatter));

        // 插入数据库
        int result = likeMapper.insert(like);
        if (result <= 0) {
            throw ExceptionUtils.createServerException("点赞失败，请重试");
        }

        // 原子更新对应内容/评论的点赞数
        int targetType = like.getTargetType();
        Long targetId = like.getTargetId();

        if (targetType == 1) {
            contentMapper.incrementLikeCount(targetId, 1);
            recommendationService.recordBehavior(like.getAnonymousId(), targetId, 2);
        } else if (targetType == 2) {
            commentService.updateCommentLikeNum(targetId, 1);
        }

        // 同步 Redis 点赞计数（仅 Key 存在时增减，防止缓存基数漂移；Redis 不可用自动降级）
        redisCacheService.incrementCountIfExists(RedisCacheService.likeCountKey(targetType, targetId), 1);

        return Result.success();
    }

    /** 取消点赞：删除记录 + 原子减少计数 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> removeLike(int targetType, Long targetId, String anonymousId) {
        // 参数校验
        ExceptionUtils.checkNotNull(targetId, "目标ID不能为空");
        ExceptionUtils.checkNotNull(anonymousId, "用户标识不能为空");
        ExceptionUtils.checkCondition(!anonymousId.trim().isEmpty(), "用户标识不能为空");

        // 执行删除
        int result = likeMapper.delete(Wrappers.<Like>lambdaQuery()
                .eq(Like::getTargetType, targetType)
                .eq(Like::getTargetId, targetId)
                .eq(Like::getAnonymousId, anonymousId));
        if (result <= 0) {
            return Result.error(400, "取消点赞失败，可能尚未点赞");
        }

        // 原子更新对应内容/评论的点赞数
        if (targetType == 1) {
            contentMapper.incrementLikeCount(targetId, -1);
            recommendationService.recordBehavior(anonymousId, targetId, -2);
        } else if (targetType == 2) {
            commentService.updateCommentLikeNum(targetId, -1);
        }

        // 同步 Redis 点赞计数（递减下限为 0，防止负值）
        redisCacheService.incrementCountIfExists(RedisCacheService.likeCountKey(targetType, targetId), -1);

        return Result.success();
    }

    /** 查询当前用户是否已对目标点过赞 */
    @Override
    @Transactional(readOnly = true)
    public Result<Boolean> checkLikeStatus(int targetType, Long targetId, String anonymousId) {
        try {
            // 参数校验
            ExceptionUtils.checkNotNull(targetId, "目标ID不能为空");
            ExceptionUtils.checkNotNull(anonymousId, "用户标识不能为空");
            ExceptionUtils.checkCondition(!anonymousId.trim().isEmpty(), "用户标识不能为空");

            // 查询点赞记录
            boolean liked = likeMapper.selectCount(Wrappers.<Like>lambdaQuery()
                    .eq(Like::getTargetType, targetType)
                    .eq(Like::getTargetId, targetId)
                    .eq(Like::getAnonymousId, anonymousId)) > 0;

            return Result.success(liked);
        } catch (Exception e) {
            return ExceptionUtils.toResult(e);
        }
    }

    /** 获取目标内容的点赞总数（缓存优先，未命中回源 DB 并回填） */
    @Override
    @Transactional(readOnly = true)
    public Result<Integer> getLikeCount(int targetType, Long targetId) {
        try {
            // 参数校验
            ExceptionUtils.checkNotNull(targetId, "目标ID不能为空");

            Integer count = redisCacheService.getIntCached(
                    RedisCacheService.likeCountKey(targetType, targetId),
                    RedisCacheService.COUNTER_TTL,
                    () -> likeMapper.selectCount(Wrappers.<Like>lambdaQuery()
                            .eq(Like::getTargetType, targetType)
                            .eq(Like::getTargetId, targetId)).intValue());

            return Result.success(count);
        } catch (Exception e) {
            return ExceptionUtils.toResult(e);
        }
    }

    /** 分页获取当前用户点过赞的内容列表 */
    @Override
    @Transactional(readOnly = true)
    public Result<List<Content>> getMyLikes(int page, int pageSize, String anonymousId) {
        try {
            // 参数校验
            ExceptionUtils.checkNotNull(anonymousId, "用户标识不能为空");
            ExceptionUtils.checkCondition(!anonymousId.trim().isEmpty(), "用户标识不能为空");
            ExceptionUtils.checkCondition(page > 0, "页码必须大于0");
            ExceptionUtils.checkCondition(pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");

            // 计算偏移量
            int offset = (page - 1) * pageSize;

            // 查询用户点赞的内容列表
            List<Content> contentList = likeMapper.getMyLikes(offset, pageSize, anonymousId);

            return Result.success(contentList);
        } catch (Exception e) {
            return ExceptionUtils.toResult(e);
        }
    }

    /** 批量查询点赞状态（用于列表页展示） */
    @Override
    @Transactional(readOnly = true)
    public Result<Map<Long, Boolean>> batchCheckLikeStatus(int targetType, List<Long> targetIds, String anonymousId) {
        try {
            // 参数校验
            ExceptionUtils.checkNotNull(targetIds, "目标ID列表不能为空");
            ExceptionUtils.checkCondition(!targetIds.isEmpty(), "目标ID列表不能为空");
            ExceptionUtils.checkCondition(targetIds.size() <= 50, "单次查询最多50个目标");
            ExceptionUtils.checkNotNull(anonymousId, "用户标识不能为空");
            ExceptionUtils.checkCondition(!anonymousId.trim().isEmpty(), "用户标识不能为空");

            // 批量查询点赞记录
            List<Like> likes = likeMapper.selectList(Wrappers.<Like>lambdaQuery()
                    .eq(Like::getTargetType, targetType)
                    .in(Like::getTargetId, targetIds)
                    .eq(Like::getAnonymousId, anonymousId));

            // 构建结果映射
            Map<Long, Boolean> resultMap = new HashMap<>();
            for (Long targetId : targetIds) {
                resultMap.put(targetId, false);
            }
            for (Like like : likes) {
                resultMap.put(like.getTargetId(), true);
            }

            return Result.success(resultMap);
        } catch (Exception e) {
            return ExceptionUtils.toResult(e);
        }
    }

    /** 批量获取点赞数量（缓存优先，未命中批量回源 DB 并回填） */
    @Override
    @Transactional(readOnly = true)
    public Result<Map<Long, Integer>> batchGetLikeCount(int targetType, List<Long> targetIds) {
        try {
            // 参数校验
            ExceptionUtils.checkNotNull(targetIds, "目标ID列表不能为空");
            ExceptionUtils.checkCondition(!targetIds.isEmpty(), "目标ID列表不能为空");
            ExceptionUtils.checkCondition(targetIds.size() <= 50, "单次查询最多50个目标");

            Map<Long, Integer> likeCountMap = redisCacheService.batchGetIntCached(
                    RedisCacheService.LIKE_COUNT_PREFIX + targetType + ":",
                    targetIds,
                    missingIds -> {
                        // 批量查询缺失目标的点赞数量（MyBatis返回List<Map>）
                        List<Map<String, Object>> resultList = likeMapper.batchGetLikeCount(targetType, missingIds);
                        Map<Long, Integer> dbCountMap = new HashMap<>();
                        for (Long targetId : missingIds) {
                            dbCountMap.put(targetId, 0);
                        }
                        for (Map<String, Object> row : resultList) {
                            Long targetId = ((Number) row.get("targetId")).longValue();
                            Integer count = ((Number) row.get("count")).intValue();
                            dbCountMap.put(targetId, count);
                        }
                        return dbCountMap;
                    });

            return Result.success(likeCountMap);
        } catch (Exception e) {
            return ExceptionUtils.toResult(e);
        }
    }

}