package com.myk.emotionalHole.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myk.emotionalHole.entity.Hug;
import com.myk.emotionalHole.entity.Like;
import com.myk.emotionalHole.mapper.HugMapper;
import com.myk.emotionalHole.mapper.LikeMapper;
import com.myk.emotionalHole.util.RedisCacheService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 点赞/抱抱计数 Redis 与 DB 对账定时任务
 *
 * 点赞/抱抱计数采用「Redis 缓存 + MySQL」双通道写入（Lua 与 DB 各自原子，但非同一事务），
 * 在 Redis 抖动、DB 更新失败、缓存失效漏删等场景下会产生缓存与 DB 不一致。
 * 本任务每 5 分钟扫描 like:count:* / hug:count:* 缓存，与 DB 真实计数（like/hug 表统计）对比，
 * 不一致时以 DB 为准校正缓存，保证计数最终一致并暴露写路径问题。
 */
@Component
public class CountReconciliationTask {

    private static final Logger logger = LoggerFactory.getLogger(CountReconciliationTask.class);

    /** 单轮对账最大处理的 Key 数，防止扫描过多阻塞任务线程 */
    private static final int MAX_KEYS_PER_ROUND = 500;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private LikeMapper likeMapper;

    @Resource
    private HugMapper hugMapper;

    /**
     * 定时对账入口：点赞 + 抱抱计数。
     * 首轮延迟 60 秒，之后每 5 分钟执行一次。
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void reconcileCounts() {
        long start = System.currentTimeMillis();
        int[] stat = {0, 0, 0}; // 对账数 / 校正数 / 失败数
        try {
            reconcileKeys(scanKeys(RedisCacheService.LIKE_COUNT_PREFIX + "*"),
                    RedisCacheService.LIKE_COUNT_PREFIX, stat,
                    (type, ids) -> {
                        List<Like> likes = likeMapper.selectList(Wrappers.<Like>lambdaQuery()
                                .eq(Like::getTargetType, type)
                                .in(Like::getTargetId, ids));
                        Map<Long, Long> counts = new HashMap<>();
                        for (Like like : likes) {
                            counts.merge(like.getTargetId(), 1L, Long::sum);
                        }
                        return counts;
                    });
            reconcileKeys(scanKeys(RedisCacheService.HUG_COUNT_PREFIX + "*"),
                    RedisCacheService.HUG_COUNT_PREFIX, stat,
                    (type, ids) -> {
                        List<Hug> hugs = hugMapper.selectList(Wrappers.<Hug>lambdaQuery()
                                .eq(Hug::getTargetType, type)
                                .in(Hug::getTargetId, ids));
                        Map<Long, Long> counts = new HashMap<>();
                        for (Hug hug : hugs) {
                            counts.merge(hug.getTargetId(), 1L, Long::sum);
                        }
                        return counts;
                    });
        } catch (Exception e) {
            logger.error("计数对账任务异常: {}", e.getMessage(), e);
        }
        logger.info("计数对账完成: 对账{}个, 校正{}个, 失败{}个, 耗时{}ms",
                stat[0], stat[1], stat[2], System.currentTimeMillis() - start);
    }

    /**
     * 对账指定前缀下的计数缓存：
     * 解析每个 Key 的目标类型与 ID，按类型批量查询 DB 真实计数，逐 Key 对比并以 DB 为准校正。
     */
    private void reconcileKeys(Set<String> keys, String prefix, int[] stat,
                               BiFunction<Integer, List<Long>, Map<Long, Long>> dbCounter) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Map<String, long[]> keyTarget = new HashMap<>();
        Map<Integer, List<Long>> idsByType = new HashMap<>();
        for (String key : keys) {
            if (stat[0] >= MAX_KEYS_PER_ROUND) {
                logger.warn("单轮对账达到上限 {} 个 Key，剩余跳过", MAX_KEYS_PER_ROUND);
                break;
            }
            long[] parsed = parseTarget(key, prefix);
            if (parsed == null) {
                continue;
            }
            stat[0]++;
            keyTarget.put(key, parsed);
            idsByType.computeIfAbsent((int) parsed[0], k -> new ArrayList<>()).add(parsed[1]);
        }

        for (Map.Entry<Integer, List<Long>> entry : idsByType.entrySet()) {
            Map<Long, Long> dbCounts = dbCounter.apply(entry.getKey(), entry.getValue());
            for (Map.Entry<String, long[]> kv : keyTarget.entrySet()) {
                long[] parsed = kv.getValue();
                if (parsed[0] != entry.getKey()) {
                    continue;
                }
                Long dbCount = dbCounts.get(parsed[1]);
                if (dbCount == null) {
                    dbCount = 0L;
                }
                fixIfMismatch(kv.getKey(), dbCount, stat);
            }
        }
    }

    /** 缓存值与 DB 值不一致时，以 DB 为准回写缓存（DB 为事实来源） */
    private void fixIfMismatch(String key, long dbCount, int[] stat) {
        try {
            Integer cached = redisCacheService.getInt(key);
            if (cached == null || cached == dbCount) {
                return;
            }
            logger.warn("计数不一致，以DB为准校正 key={}, 缓存={}, DB={}", key, cached, dbCount);
            redisCacheService.set(key, String.valueOf(dbCount), RedisCacheService.COUNTER_TTL);
            stat[1]++;
        } catch (Exception e) {
            logger.error("校正计数失败 key={}: {}", key, e.getMessage());
            stat[2]++;
        }
    }

    /** 解析计数缓存 Key：{prefix}{targetType}:{targetId} */
    private long[] parseTarget(String key, String prefix) {
        try {
            String suffix = key.substring(prefix.length());
            String[] parts = suffix.split(":");
            if (parts.length != 2) {
                logger.warn("跳过无法解析的计数缓存Key: {}", key);
                return null;
            }
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (Exception e) {
            logger.warn("跳过无法解析的计数缓存Key: {}", key);
            return null;
        }
    }

    /** 扫描匹配的 Redis Key；Redis 不可用时返回空集并降级跳过本轮 */
    private Set<String> scanKeys(String pattern) {
        try {
            return stringRedisTemplate.keys(pattern);
        } catch (Exception e) {
            logger.warn("Redis 不可用，本轮对账跳过（pattern={}）: {}", pattern, e.getMessage());
            return Collections.emptySet();
        }
    }
}
