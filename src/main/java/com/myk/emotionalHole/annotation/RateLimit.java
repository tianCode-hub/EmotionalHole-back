package com.myk.emotionalHole.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 配合 RateLimitAspect 切面实现固定窗口限流：
 * 同一维度（默认按 anonymousId，缺省按 IP）在 windowSeconds 窗口内最多允许 maxCount 次访问，
 * 超过则返回 429 错误。存储优先 Redis，Redis 不可用时自动降级为本地内存限流。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流标识前缀，如 "publishContent"、"submitReport" */
    String key();

    /** 时间窗口（秒），默认 60 */
    int windowSeconds() default 60;

    /** 窗口内最大访问次数，默认 10 */
    int maxCount() default 10;

    /** 超限提示语 */
    String message() default "操作过于频繁，请稍后再试";
}
