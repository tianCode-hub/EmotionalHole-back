package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.common.Result;
import com.myk.emotionalHole.entity.Message;
import com.myk.emotionalHole.service.AiAsyncService;
import com.myk.emotionalHole.service.AiService;
import com.myk.emotionalHole.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI聊天服务实现类
 * 基于百度千帆大模型实现情感陪伴对话
 * 流程：用户消息 → 调用千帆API → 保存对话记录 → 返回AI回复
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceImpl.class);

    /** AI 对话等待千帆回复的最长等待时间（秒）：大模型全量生成回复通常需 5~15s，3s 必超时 */
    private static final long AI_REPLY_TIMEOUT_SECONDS = 15;

    @Autowired
    private AiAsyncService aiAsyncService;

    @Autowired
    private MessageService messageService;

    // AI对话：千帆调用在独立线程池执行，最多等待3秒，超时降级兜底话术
    @Override
    public Result<String> aiChat(String anonymousId, String userInput) {
        try {
            // 千帆大模型调用放入 aiTaskExecutor 线程池异步执行，避免占用 Web 容器线程
            CompletableFuture<String> future = aiAsyncService.requestAiReply(userInput);
            String aiResponse;
            try {
                aiResponse = future.get(AI_REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.warn("AI回复等待超时({}s)，返回兜底话术，anonymousId: {}", AI_REPLY_TIMEOUT_SECONDS, anonymousId);
                aiResponse = "抱歉，AI思考时间较长，请稍后再试。";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                aiResponse = "抱歉，AI服务暂时不可用，请稍后再试。";
            } catch (ExecutionException e) {
                logger.error("AI回复执行失败: {}", e.getMessage(), e);
                aiResponse = "抱歉，AI服务暂时不可用，请稍后再试。";
            }

            // 对话历史异步保存，不阻塞当前请求
            aiAsyncService.saveChatHistory(anonymousId, userInput, aiResponse);

            logger.info("AI对话成功，匿名用户ID: {}, 用户输入: {}", anonymousId, userInput);
            return Result.success(aiResponse);
        } catch (Exception e) {
            logger.error("AI对话失败: {}", e.getMessage(), e);
            return Result.error(500, "AI对话失败，请重试");
        }
    }

    // 获取用户与AI的对话历史
    @Override
    public Result<List<Message>> getChatHistory(String anonymousId) {
        try {
            Result<List<Message>> result = messageService.getConversation(anonymousId, "ai_assistant", 0, 100);
            logger.info("获取AI对话历史成功，匿名用户ID: {}, 历史记录数: {}", anonymousId, result.getData() != null ? result.getData().size() : 0);
            return result;
        } catch (Exception e) {
            logger.error("获取AI对话历史失败: {}", e.getMessage(), e);
            return Result.error(500, "获取对话历史失败，请重试");
        }
    }

    // 清空用户与AI的对话历史
    @Override
    public Result<Boolean> deleteChatHistory(String anonymousId) {
        try {
            Result<List<Message>> result = messageService.getConversation(anonymousId, "ai_assistant", 0, 1000);
            if (result.getCode() == 200 && result.getData() != null) {
                for (Message msg : result.getData()) {
                    messageService.deleteMessage(msg.getId(), anonymousId);
                }
            }
            logger.info("删除AI对话历史成功，匿名用户ID: {}", anonymousId);
            return Result.success(true);
        } catch (Exception e) {
            logger.error("删除AI对话历史失败: {}", e.getMessage(), e);
            return Result.error(500, "删除对话历史失败，请重试");
        }
    }
}