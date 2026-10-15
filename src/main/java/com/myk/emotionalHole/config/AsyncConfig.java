package com.myk.emotionalHole.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * aiTaskExecutor 用于 AI 对话中的千帆大模型调用与对话历史保存，
 * 将耗时的外部 API 调用与数据库写入从 Web 容器线程中剥离，
 * 提升接口并发处理能力；队列满时由调用方线程执行（CallerRunsPolicy），避免任务静默丢失。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** AI 任务线程池：核心4 / 最大8 / 队列100 */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
