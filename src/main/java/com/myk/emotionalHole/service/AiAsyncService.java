package com.myk.emotionalHole.service;

import java.util.concurrent.CompletableFuture;

/**
 * AI 异步任务服务
 *
 * 将千帆大模型调用与对话历史保存放入独立线程池执行，
 * 避免长时间阻塞 Web 容器线程，提升 AI 对话接口并发能力。
 */
public interface AiAsyncService {

    /** 异步调用千帆 Agent 获取 AI 回复 */
    CompletableFuture<String> requestAiReply(String userInput);

    /** 异步保存用户消息与 AI 回复到对话历史 */
    void saveChatHistory(String anonymousId, String userInput, String aiResponse);
}
