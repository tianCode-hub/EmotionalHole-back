package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.common.Result;
import com.myk.emotionalHole.entity.Hug;
import com.myk.emotionalHole.mapper.ContentMapper;
import com.myk.emotionalHole.mapper.HugMapper;
import com.myk.emotionalHole.service.RecommendationService;
import com.myk.emotionalHole.util.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 抱抱服务单元测试：验证送抱抱/取消、防重复与 Redis 计数同步
 */
class HugServiceImplTest {

    @Mock
    private HugMapper hugMapper;
    @Mock
    private ContentMapper contentMapper;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private HugServiceImpl hugService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Hug buildHug(int targetType, Long targetId, String anonymousId) {
        Hug hug = new Hug();
        hug.setTargetType(targetType);
        hug.setTargetId(targetId);
        hug.setAnonymousId(anonymousId);
        return hug;
    }

    @Test
    void shouldAddHugAndSyncRedisCounter() {
        Hug hug = buildHug(1, 200L, "u1");
        when(hugMapper.selectCount(any())).thenReturn(0L);
        when(hugMapper.insert(any(Hug.class))).thenReturn(1);
        when(contentMapper.incrementHugCount(200L, 1)).thenReturn(1);

        Result<Void> result = hugService.addHug(hug);

        assertEquals(200, result.getCode());
        verify(contentMapper).incrementHugCount(200L, 1);
        verify(recommendationService).recordBehavior("u1", 200L, 4);
        verify(redisCacheService).incrementCountIfExists("hug:count:1:200", 1);
    }

    @Test
    void shouldRejectDuplicateHug() {
        Hug hug = buildHug(1, 200L, "u1");
        when(hugMapper.selectCount(any())).thenReturn(1L);

        Result<Void> result = hugService.addHug(hug);

        assertEquals(400, result.getCode());
        verify(hugMapper, never()).insert(any(Hug.class));
        verify(redisCacheService, never()).incrementCountIfExists(any(), anyLong());
    }

    @Test
    void shouldRemoveHugAndSyncRedisCounter() {
        when(hugMapper.delete(any())).thenReturn(1);
        when(contentMapper.incrementHugCount(200L, -1)).thenReturn(1);

        Result<Void> result = hugService.removeHug(1, 200L, "u1");

        assertEquals(200, result.getCode());
        verify(contentMapper).incrementHugCount(200L, -1);
        verify(redisCacheService).incrementCountIfExists("hug:count:1:200", -1);
    }

    @Test
    void shouldFailRemoveWhenNotHugged() {
        when(hugMapper.delete(any())).thenReturn(0);
        Result<Void> result = hugService.removeHug(1, 200L, "u1");
        assertEquals(400, result.getCode());
        verify(redisCacheService, never()).incrementCountIfExists(any(), anyLong());
    }

    @Test
    void shouldGetHugCountFromCache() {
        when(redisCacheService.getIntCached(eq("hug:count:1:200"), anyLong(), any()))
                .thenReturn(6);

        Result<Integer> result = hugService.getHugCount(1, 200L);

        assertEquals(6, result.getData());
        verify(hugMapper, never()).selectCount(any());
    }

    @Test
    void shouldFallbackToDbWhenCacheMiss() {
        when(redisCacheService.getIntCached(eq("hug:count:1:200"), anyLong(), any()))
                .thenAnswer(inv -> ((Supplier<Integer>) inv.getArgument(2)).get());
        when(hugMapper.selectCount(any())).thenReturn(4L);

        Result<Integer> result = hugService.getHugCount(1, 200L);

        assertEquals(4, result.getData());
        verify(hugMapper).selectCount(any());
    }
}
