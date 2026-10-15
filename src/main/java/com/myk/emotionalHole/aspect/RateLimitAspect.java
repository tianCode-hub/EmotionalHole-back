package com.myk.emotionalHole.aspect;

import com.myk.emotionalHole.annotation.RateLimit;
import com.myk.emotionalHole.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口限流切面
 * 固定窗口限流：按 anonymousId（缺省用请求 IP）在窗口内计数，
 * 超过 maxCount 直接返回 429 Result，不再执行目标方法。
 * 存储策略：Redis（INCR+EXPIRE）优先；Redis 不可用时降级本地内存窗口，保证应用可用。
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);

    private static final String KEY_PREFIX = "rate:limit:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 本地内存兜底窗口：key -> 窗口起始时间与当前计数 */
    private final Map<String, LocalRateWindow> localWindows = new ConcurrentHashMap<>();

//    aop+自定义注解实现限流操作
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String fullKey = KEY_PREFIX + rateLimit.key() + ":" + resolveIdentity();
        boolean allowed;
        try {
            allowed = tryRedisRateLimit(fullKey, rateLimit);
        } catch (Exception e) {
            logger.warn("Redis 限流不可用，降级本地内存限流，key={}: {}", fullKey, e.getMessage());
            allowed = tryLocalRateLimit(fullKey, rateLimit);
        }
        if (!allowed) {
            logger.info("限流触发，key={}，windowSeconds={}，maxCount={}", fullKey, rateLimit.windowSeconds(), rateLimit.maxCount());
            return Result.error(429, rateLimit.message());
        }
        return pjp.proceed();
    }

    /** 获取限流维度标识：优先 anonymousId，缺省使用请求 IP */
    private String resolveIdentity() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Object anonymousId = request.getAttribute("anonymousId");
                if (anonymousId != null && !anonymousId.toString().isEmpty()) {
                    return anonymousId.toString();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.warn("获取限流标识失败，使用 unknown: {}", e.getMessage());
        }
        return "unknown";
    }

    /** Redis 固定窗口计数：I·NCR + 首次设置过期时间 */
    private boolean tryRedisRateLimit(String fullKey, RateLimit rateLimit) {
        Long count = stringRedisTemplate.opsForValue().increment(fullKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(fullKey, Duration.ofSeconds(rateLimit.windowSeconds()));
        }
        return count != null && count <= rateLimit.maxCount();
    }

    /** 本地内存固定窗口计数（Redis 不可用时兜底） */
    private boolean tryLocalRateLimit(String fullKey, RateLimit rateLimit) {
        long now = System.currentTimeMillis();
        LocalRateWindow window = localWindows.compute(fullKey, (k, w) -> {
            if (w == null || now - w.windowStartMillis >= rateLimit.windowSeconds() * 1000L) {
                return new LocalRateWindow(now, 1);
            }
            w.count++;
            return w;
        });
        return window.count <= rateLimit.maxCount();
    }

    /** 本地内存限流窗口 */
    private static class LocalRateWindow {
        private final long windowStartMillis;
        private long count;

        private LocalRateWindow(long windowStartMillis, long count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}
