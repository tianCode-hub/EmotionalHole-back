package com.myk.emotionalHole.util;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务
 * 统一封装热点数据缓存与点赞/抱抱计数的读写，所有方法自带降级：
 * Redis 不可用时捕获异常并返回空值/默认值，业务层无需感知，直接回源数据库。
 *
 * 缓存 Key 规则：
 *  - like:count:{targetType}:{targetId}   点赞数计数
 *  - hug:count:{targetType}:{targetId}    抱抱数计数
 *  - content:list:{page}:{pageSize}       首页内容列表
 *  - hot:ranking:{pageNum}:{pageSize}     热榜数据
 *  - content:detail:{id}                  内容详情
 */
@Component
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    /** 计数类缓存 Key 前缀 */
    public static final String LIKE_COUNT_PREFIX = "like:count:";
    public static final String HUG_COUNT_PREFIX = "hug:count:";
    /** 热点数据缓存 Key 前缀 */
    public static final String CONTENT_LIST_PREFIX = "content:list:";
    public static final String HOT_RANKING_PREFIX = "hot:ranking:";
    public static final String CONTENT_DETAIL_PREFIX = "content:detail:";

    /** 计数缓存 TTL：1 小时（写操作会续期，读可回源自愈） */
    public static final long COUNTER_TTL = 3600L;
    /** 首页列表缓存 TTL：60 秒 */
    public static final long CONTENT_LIST_TTL = 60L;
    /** 热榜缓存 TTL：120 秒（热度含时间衰减，短 TTL 自动刷新） */
    public static final long HOT_RANKING_TTL = 120L;
    /** 详情缓存 TTL：60 秒 */
    public static final long CONTENT_DETAIL_TTL = 60L;

    /**
     * 仅在 Key 存在时对计数加减（防止缓存未初始化时产生与 DB 不一致的基数），
     * 递减下限为 0，与 DB 端 GREATEST 语义保持一致。Key 不存在返回 -1。
     * Lua 脚本保证原子性。
     */
    private static final String INCR_IF_EXISTS_LUA =
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "  local v = redis.call('get', KEYS[1]) " +
            "  local r = tonumber(v) + tonumber(ARGV[1]) " +
            "  if r < 0 then r = 0 end " +
            "  redis.call('set', KEYS[1], r) " +
            "  return r " +
            "end " +
            "return -1";

    /** 按模式删除 Key（SCAN 语义：KEYS 阻塞仅限小规模项目） */
    private static final String DEL_BY_PATTERN_LUA =
            "local keys = redis.call('keys', KEYS[1]) " +
            "if #keys == 0 then return 0 end " +
            "for _, k in ipairs(keys) do redis.call('del', k) end " +
            "return #keys";

    private final DefaultRedisScript<Long> incrIfExistsScript =
            new DefaultRedisScript<>(INCR_IF_EXISTS_LUA, Long.class);
    private final DefaultRedisScript<Long> delByPatternScript =
            new DefaultRedisScript<>(DEL_BY_PATTERN_LUA, Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /* ==================== Key 构建 ==================== */

    public static String likeCountKey(int targetType, Long targetId) {
        return LIKE_COUNT_PREFIX + targetType + ":" + targetId;
    }

    public static String hugCountKey(int targetType, Long targetId) {
        return HUG_COUNT_PREFIX + targetType + ":" + targetId;
    }

    public static String contentListKey(int page, int pageSize) {
        return CONTENT_LIST_PREFIX + page + ":" + pageSize;
    }

    public static String hotRankingKey(int pageNum, int pageSize) {
        return HOT_RANKING_PREFIX + pageNum + ":" + pageSize;
    }

    public static String contentDetailKey(Long id) {
        return CONTENT_DETAIL_PREFIX + id;
    }

    /* ==================== 计数操作（写，原子 + 防负） ==================== */

    /**
     * 仅当 Key 存在时增减计数（delta 为负表示递减），返回操作后的值；Key 不存在返回 -1。
     */
    public long incrementCountIfExists(String key, long delta) {
        try {
            Long result = stringRedisTemplate.execute(incrIfExistsScript,
                    java.util.Collections.singletonList(key), String.valueOf(delta));
            return result == null ? -1 : result;
        } catch (Exception e) {
            log.warn("Redis 计数增减失败，key={}, delta={}: {}", key, delta, e.getMessage());
            return -1;
        }
    }

    /* ==================== 计数操作（读，命中返回 / 未命中回源 DB 并回填） ==================== */

    /** 读取计数，Key 不存在返回 null（由调用方回源 DB） */
    public Integer getInt(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? null : Integer.parseInt(value);
        } catch (Exception e) {
            log.warn("Redis 读取失败，key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /** 读取计数：命中缓存直接返回，未命中调用 dbLoader 回源并回填缓存 */
    public Integer getIntCached(String key, long ttlSeconds, Supplier<Integer> dbLoader) {
        Integer cached = getInt(key);
        if (cached != null) {
            return cached;
        }
        Integer value = dbLoader.get();
        if (value != null) {
            set(key, String.valueOf(value), ttlSeconds);
        }
        return value;
    }

    /**
     * 批量读取计数：mGet 命中直接返回，未命中批量回源 DB 并回填缓存。
     * dbLoader 入参为缺失的 ID 列表，返回 id → 计数 的映射。
     */
    public Map<Long, Integer> batchGetIntCached(String keyPrefix, Collection<Long> ids,
                                                Function<List<Long>, Map<Long, Integer>> dbLoader) {
        Map<Long, Integer> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>(ids);
        List<String> keys = new ArrayList<>(idList.size());
        for (Long id : idList) {
            keys.add(keyPrefix + id);
        }

        List<String> cachedValues = mGet(keys);
        List<Long> missingIds = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            String v = (cachedValues == null) ? null : cachedValues.get(i);
            if (v != null && !v.isEmpty()) {
                result.put(idList.get(i), Integer.parseInt(v));
            } else {
                missingIds.add(idList.get(i));
            }
        }

        if (!missingIds.isEmpty()) {
            Map<Long, Integer> dbValues = dbLoader.apply(missingIds);
            Map<String, String> backfill = new HashMap<>();
            for (Map.Entry<Long, Integer> entry : dbValues.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
                backfill.put(keyPrefix + entry.getKey(), String.valueOf(entry.getValue()));
            }
            multiSet(backfill, COUNTER_TTL);
        }
        return result;
    }

    /* ==================== 字符串操作 ==================== */

    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 读取失败，key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public List<String> mGet(Collection<String> keys) {
        try {
            return stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("Redis 批量读取失败: {}", e.getMessage());
            return null;
        }
    }

    public void set(String key, String value, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 写入失败，key={}: {}", key, e.getMessage());
        }
    }

    /** 批量写入并统一设置 TTL（pipeline 减少网络往返） */
    public void multiSet(Map<String, String> keyValues, long ttlSeconds) {
        if (keyValues == null || keyValues.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                keyValues.forEach((key, value) -> {
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    connection.set(keyBytes, value.getBytes(StandardCharsets.UTF_8));
                    connection.expire(keyBytes, ttlSeconds);
                });
                return null;
            });
        } catch (Exception e) {
            log.warn("Redis 批量写入失败: {}", e.getMessage());
        }
    }

    /** 按模式删除缓存（用于内容发布/删除/审核后失效列表缓存） */
    public void deleteByPattern(String pattern) {
        try {
            stringRedisTemplate.execute(delByPatternScript,
                    java.util.Collections.singletonList(pattern));
        } catch (Exception e) {
            log.warn("Redis 按模式删除失败，pattern={}: {}", pattern, e.getMessage());
        }
    }

    public void delete(String... keys) {
        try {
            if (keys != null && keys.length > 0) {
                stringRedisTemplate.delete(List.of(keys));
            }
        } catch (Exception e) {
            log.warn("Redis 删除失败: {}", e.getMessage());
        }
    }

    /* ==================== JSON 操作（热点数据） ==================== */

    public void setJson(String key, Object value, long ttlSeconds) {
        set(key, JSONUtil.toJsonStr(value), ttlSeconds);
    }

    public <T> T getJson(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.toBean(json, clazz);
        } catch (Exception e) {
            log.warn("Redis JSON 反序列化失败，key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public <T> List<T> getJsonList(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.toList(json, clazz);
        } catch (Exception e) {
            log.warn("Redis JSON 列表反序列化失败，key={}: {}", key, e.getMessage());
            return null;
        }
    }
}
