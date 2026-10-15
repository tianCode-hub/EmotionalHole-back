package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.entity.Message;
import com.myk.emotionalHole.service.AiAsyncService;
import com.myk.emotionalHole.service.MessageService;
import com.myk.emotionalHole.util.AiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 异步任务实现类
 *
 * requestAiReply 与 saveChatHistory 均在 aiTaskExecutor 线程池中异步执行，
 * 千帆 API 调用与对话历史写入不占用 Web 容器线程。
 */
@Service
public class AiAsyncServiceImpl implements AiAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(AiAsyncServiceImpl.class);

    @Resource
    private AiUtils aiUtils;

    @Resource
    private MessageService messageService;

    /** 异步调用千帆 Agent 获取 AI 回复 */
    @Override
    @Async("aiTaskExecutor")
    public CompletableFuture<String> requestAiReply(String userInput) {
        Map<String, Object> aiResult = aiUtils.getAiResponseWithEmotion(userInput);
        return CompletableFuture.completedFuture((String) aiResult.get("response"));
    }

    /** 异步保存用户消息与 AI 回复到对话历史（失败仅记日志，不影响主流程） */
    @Override
    @Async("aiTaskExecutor")
    public void saveChatHistory(String anonymousId, String userInput, String aiResponse) {
        try {
            if (anonymousId == null || anonymousId.isEmpty()) {
                logger.info("用户ID无效，跳过对话历史存储");
                return;
            }

            Message userMessage = new Message();
            userMessage.setSenderAnonymousId(anonymousId);
            userMessage.setReceiverAnonymousId("ai_assistant");
            userMessage.setMessageText(userInput);
            userMessage.setMessageStatus(1);
            userMessage.setMessageType(2);
            messageService.sendMessage(userMessage);

            Message aiMessage = new Message();
            aiMessage.setSenderAnonymousId("ai_assistant");
            aiMessage.setReceiverAnonymousId(anonymousId);
            aiMessage.setMessageText(aiResponse);
            aiMessage.setMessageStatus(1);
            aiMessage.setMessageType(2);
            messageService.sendMessage(aiMessage);
        } catch (Exception e) {
            logger.error("保存对话历史失败: {}", e.getMessage(), e);
        }
    }
}
