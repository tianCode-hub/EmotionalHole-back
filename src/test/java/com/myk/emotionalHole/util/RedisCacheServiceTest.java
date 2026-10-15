package com.myk.emotionalHole.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Redis 缓存服务单元测试（mock StringRedisTemplate，验证缓存读写与降级逻辑）
 */
class RedisCacheServiceTest {

    private RedisCacheService cacheService;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cacheService = new RedisCacheService();
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        // doReturn 风格避免直接调用 mock 上的真实方法（RedisTemplate 类方法）
        doReturn(valueOperations).when(stringRedisTemplate).opsForValue();
        ReflectionTestUtils.setField(cacheService, "stringRedisTemplate", stringRedisTemplate);
    }

    @Test
    void shouldBuildCountKey() {
        assertEquals("like:count:1:100", RedisCacheService.likeCountKey(1, 100L));
        assertEquals("hug:count:2:50", RedisCacheService.hugCountKey(2, 50L));
    }

    @Test
    void shouldGetParsedInt() {
        doReturn("5").when(valueOperations).get("like:count:1:100");
        assertEquals(5, cacheService.getInt("like:count:1:100"));
    }

    @Test
    void shouldReturnNullWhenKeyAbsent() {
        doReturn(null).when(valueOperations).get(anyString());
        assertNull(cacheService.getInt("like:count:1:100"));
    }

    @Test
    void shouldReadFromCacheAndSkipDbLoader() {
        doReturn("8").when(valueOperations).get("like:count:1:100");
        Supplier<Integer> dbLoader = mock(Supplier.class);
        Integer result = cacheService.getIntCached("like:count:1:100", 3600L, dbLoader);
        assertEquals(8, result);
        verify(dbLoader, never()).get();
    }

    @Test
    void shouldFallbackToDbAndBackfillOnMiss() {
        doReturn(null).when(valueOperations).get(anyString());
        Integer result = cacheService.getIntCached("like:count:1:100", 3600L, () -> 3);
        assertEquals(3, result);
        verify(valueOperations).set(eq("like:count:1:100"), eq("3"), eq(3600L), any(TimeUnit.class));
    }

    @Test
    void shouldBatchReadHitAndBackfillMiss() {
        // 第一个命中缓存，第二个缺失（Arrays.asList 允许 null 表示缺失）
        doReturn(Arrays.asList("5", null)).when(valueOperations).multiGet(anyList());

        Map<Long, Integer> dbResult = new HashMap<>();
        dbResult.put(2L, 7);
        Map<Long, Integer> result = cacheService.batchGetIntCached(
                "like:count:1:",
                List.of(1L, 2L),
                ids -> {
                    assertEquals(List.of(2L), ids);
                    return dbResult;
                });

        assertEquals(5, result.get(1L));
        assertEquals(7, result.get(2L));
        verify(stringRedisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    void shouldDegradeGracefullyWhenRedisFails() {
        doThrow(new RuntimeException("redis down")).when(valueOperations).get(anyString());
        assertNull(cacheService.getInt("like:count:1:100"));
        // 降级后走 DB 回源逻辑，调用方仍可拿到 DB 值
        Integer result = cacheService.getIntCached("like:count:1:100", 3600L, () -> 9);
        assertEquals(9, result);
    }

    @Test
    void shouldReturnNegativeOneWhenIncrementKeyAbsent() {
        doReturn(-1L).when(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any());
        assertEquals(-1L, cacheService.incrementCountIfExists("like:count:1:100", 1));
    }

    @Test
    void shouldReturnNegativeOneWhenIncrementFails() {
        doThrow(new RuntimeException("redis down"))
                .when(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any());
        assertEquals(-1L, cacheService.incrementCountIfExists("like:count:1:100", -1));
    }

    @Test
    void shouldHandleNullIdsInBatch() {
        assertEquals(0, cacheService.batchGetIntCached("like:count:1:", List.of(), ids -> Map.of()).size());
    }

    @Test
    void shouldSetStringWithTtl() {
        cacheService.set("key", "value", 60L);
        verify(valueOperations).set("key", "value", 60L, TimeUnit.SECONDS);
    }

    @Test
    void shouldSetJsonAndGetListRoundTrip() {
        // 使用真实的 get/set 逻辑（mock 底层模板），验证 JSON 序列化往返
        doReturn("[{\"id\":1},{\"id\":2}]").when(valueOperations).get("content:list:1:10");
        List<com.myk.emotionalHole.entity.Content> list =
                cacheService.getJsonList("content:list:1:10", com.myk.emotionalHole.entity.Content.class);
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getId());
    }
}
