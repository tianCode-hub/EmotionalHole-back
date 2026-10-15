package com.myk.emotionalHole.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * AI 工具类单元测试：验证 AI 不可用时的降级话术与关键词情感分析
 */
class AiUtilsTest {

    private AiUtils aiUtils;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        aiUtils = new AiUtils();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiUtils, "restTemplate", restTemplate);
    }

    @Test
    void shouldReturnFallbackWhenAgentIdNotConfigured() {
        // 不配置 agentId，直接返回配置缺失提示
        ReflectionTestUtils.setField(aiUtils, "apiKey", "key");
        ReflectionTestUtils.setField(aiUtils, "agentId", null);
        ReflectionTestUtils.setField(aiUtils, "agentApiUrl", "http://localhost:8080");

        String response = aiUtils.getAiResponse("你好", null);
        assertEquals("抱歉，Agent ID未配置，请联系管理员。", response);
    }

    @Test
    void shouldReturnFallbackWhenApiUrlNotConfigured() {
        ReflectionTestUtils.setField(aiUtils, "apiKey", "key");
        ReflectionTestUtils.setField(aiUtils, "agentId", "agent");
        ReflectionTestUtils.setField(aiUtils, "agentApiUrl", null);

        String response = aiUtils.getAiResponse("你好", null);
        assertEquals("抱歉，Agent API URL未配置，请联系管理员。", response);
    }

    @Test
    void shouldDegradeWhenAiCallFails() {
        ReflectionTestUtils.setField(aiUtils, "apiKey", "key");
        ReflectionTestUtils.setField(aiUtils, "agentId", "agent");
        ReflectionTestUtils.setField(aiUtils, "agentApiUrl", "http://localhost:9999/fake");
        org.mockito.Mockito.when(restTemplate.postForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        String response = aiUtils.getAiResponse("今天心情很差", null);
        assertEquals("抱歉，AI服务暂时不可用，请稍后再试。", response);
    }

    @Test
    void shouldDegradeInResponseWithEmotion() {
        ReflectionTestUtils.setField(aiUtils, "apiKey", "key");
        ReflectionTestUtils.setField(aiUtils, "agentId", "agent");
        ReflectionTestUtils.setField(aiUtils, "agentApiUrl", "http://localhost:9999/fake");
        org.mockito.Mockito.when(restTemplate.postForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> result = aiUtils.getAiResponseWithEmotion("我不想活了");
        assertEquals("抱歉，AI服务暂时不可用，请稍后再试。", result.get("response"));
    }

    @Test
    void shouldAnalyzeNegativeEmotionByKeywords() {
        AiUtils.EmotionAnalysisResult result = aiUtils.analyzeEmotion("我很难过，感觉非常绝望和痛苦");
        assertNotNull(result);
        assertEquals("negative", result.getEmotion());
        assertTrue(result.getScore() > 0.5);
    }

    @Test
    void shouldAnalyzePositiveEmotionByKeywords() {
        AiUtils.EmotionAnalysisResult result = aiUtils.analyzeEmotion("今天很开心，感觉幸福又满足");
        assertNotNull(result);
        assertEquals("positive", result.getEmotion());
        assertTrue(result.getScore() > 0.5);
    }

    @Test
    void shouldAnalyzeNeutralWhenNoKeyword() {
        AiUtils.EmotionAnalysisResult result = aiUtils.analyzeEmotion("今天吃了面条");
        assertNotNull(result);
        assertEquals("neutral", result.getEmotion());
        assertEquals(0.5, result.getScore());
    }
}
