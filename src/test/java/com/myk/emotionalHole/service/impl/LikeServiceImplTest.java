package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.common.Result;
import com.myk.emotionalHole.entity.Like;
import com.myk.emotionalHole.mapper.ContentMapper;
import com.myk.emotionalHole.mapper.LikeMapper;
import com.myk.emotionalHole.service.CommentService;
import com.myk.emotionalHole.service.RecommendationService;
import com.myk.emotionalHole.util.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点赞服务单元测试：验证点赞/取消、防重复、Redis 计数同步与缓存回源
 */
class LikeServiceImplTest {

    @Mock
    private LikeMapper likeMapper;
    @Mock
    private ContentMapper contentMapper;
    @Mock
    private CommentService commentService;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private LikeServiceImpl likeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Like buildLike(int targetType, Long targetId, String anonymousId) {
        Like like = new Like();
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        like.setAnonymousId(anonymousId);
        return like;
    }

    @Test
    void shouldAddLikeAndSyncRedisCounter() {
        Like like = buildLike(1, 100L, "u1");
        when(likeMapper.selectCount(any())).thenReturn(0L);
        when(likeMapper.insert(any(Like.class))).thenReturn(1);
        when(contentMapper.incrementLikeCount(100L, 1)).thenReturn(1);

        Result<Void> result = likeService.addLike(like);

        assertEquals(200, result.getCode());
        verify(contentMapper).incrementLikeCount(100L, 1);
        verify(recommendationService).recordBehavior("u1", 100L, 2);
        verify(redisCacheService).incrementCountIfExists("like:count:1:100", 1);
    }

    @Test
    void shouldRejectDuplicateLike() {
        Like like = buildLike(1, 100L, "u1");
        when(likeMapper.selectCount(any())).thenReturn(1L);

        Result<Void> result = likeService.addLike(like);

        assertEquals(400, result.getCode());
        verify(likeMapper, never()).insert(any(Like.class));
        verify(redisCacheService, never()).incrementCountIfExists(anyString(), anyLong());
    }

    @Test
    void shouldRemoveLikeAndSyncRedisCounter() {
        when(likeMapper.delete(any())).thenReturn(1);
        when(contentMapper.incrementLikeCount(100L, -1)).thenReturn(1);

        Result<Void> result = likeService.removeLike(1, 100L, "u1");

        assertEquals(200, result.getCode());
        verify(contentMapper).incrementLikeCount(100L, -1);
        verify(recommendationService).recordBehavior("u1", 100L, -2);
        verify(redisCacheService).incrementCountIfExists("like:count:1:100", -1);
    }

    @Test
    void shouldFailRemoveWhenNotLiked() {
        when(likeMapper.delete(any())).thenReturn(0);
        Result<Void> result = likeService.removeLike(1, 100L, "u1");
        assertEquals(400, result.getCode());
        verify(redisCacheService, never()).incrementCountIfExists(anyString(), anyLong());
    }

    @Test
    void shouldGetLikeCountFromCacheWithoutTouchingDb() {
        when(redisCacheService.getIntCached(eq("like:count:1:100"), anyLong(), any()))
                .thenReturn(5);

        Result<Integer> result = likeService.getLikeCount(1, 100L);

        assertEquals(200, result.getCode());
        assertEquals(5, result.getData());
        verify(likeMapper, never()).selectCount(any());
    }

    @Test
    void shouldFallbackToDbWhenCacheMiss() {
        when(redisCacheService.getIntCached(eq("like:count:1:100"), anyLong(), any()))
                .thenAnswer(inv -> ((Supplier<Integer>) inv.getArgument(2)).get());
        when(likeMapper.selectCount(any())).thenReturn(3L);

        Result<Integer> result = likeService.getLikeCount(1, 100L);

        assertEquals(3, result.getData());
        verify(likeMapper).selectCount(any());
    }

    @Test
    void shouldBatchGetCountFromCache() {
        Map<Long, Integer> cached = new HashMap<>();
        cached.put(1L, 10);
        cached.put(2L, 20);
        when(redisCacheService.batchGetIntCached(anyString(), any(), any())).thenReturn(cached);

        Result<Map<Long, Integer>> result = likeService.batchGetLikeCount(1, List.of(1L, 2L));

        assertEquals(10, result.getData().get(1L));
        assertEquals(20, result.getData().get(2L));
        verify(likeMapper, never()).batchGetLikeCount(anyInt(), any());
    }

    @Test
    void shouldBackfillMissingCountsFromDb() {
        // 模拟缓存全部未命中：调用 service 传入的 dbLoader 回源并映射结果
        when(redisCacheService.batchGetIntCached(anyString(), any(), any()))
                .thenAnswer(inv -> {
                    List<Long> missingIds = inv.getArgument(1);
                    Function<List<Long>, Map<Long, Integer>> loader = inv.getArgument(2);
                    return loader.apply(missingIds);
                });
        Map<String, Object> row = new HashMap<>();
        row.put("targetId", 2L);
        row.put("count", 7);
        when(likeMapper.batchGetLikeCount(anyInt(), any())).thenReturn(List.of(row));

        Result<Map<Long, Integer>> result = likeService.batchGetLikeCount(1, List.of(2L));

        assertNotNull(result.getData());
        assertEquals(200, result.getCode());
        assertEquals(7, result.getData().get(2L));
        verify(likeMapper).batchGetLikeCount(anyInt(), any());
    }

    @Test
    void shouldValidateBatchSizeLimit() {
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            ids.add(i);
        }
        Result<Map<Long, Integer>> result = likeService.batchGetLikeCount(1, ids);
        assertEquals(400, result.getCode());
    }
}
