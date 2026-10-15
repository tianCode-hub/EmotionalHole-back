package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.common.Result;
import com.myk.emotionalHole.entity.Content;
import com.myk.emotionalHole.entity.SensitiveWord;
import com.myk.emotionalHole.mapper.CommentMapper;
import com.myk.emotionalHole.mapper.ContentMapper;
import com.myk.emotionalHole.mapper.HugMapper;
import com.myk.emotionalHole.mapper.LikeMapper;
import com.myk.emotionalHole.mapper.ReportMapper;
import com.myk.emotionalHole.mapper.TopicMapper;
import com.myk.emotionalHole.mapper.UserMapper;
import com.myk.emotionalHole.service.AtmosphereService;
import com.myk.emotionalHole.service.EmotionAnalysisService;
import com.myk.emotionalHole.service.RiskAssessmentService;
import com.myk.emotionalHole.util.ExceptionUtils;
import com.myk.emotionalHole.util.RedisCacheService;
import com.myk.emotionalHole.util.SensitiveWordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容服务单元测试：发布流程的敏感词拦截、审核状态流转与缓存失效
 */
class ContentServiceImplTest {

    @Mock
    private ContentMapper contentMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TopicMapper topicMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private LikeMapper likeMapper;
    @Mock
    private HugMapper hugMapper;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private AtmosphereService atmosphereService;
    @Mock
    private RiskAssessmentService riskAssessmentService;
    @Mock
    private EmotionAnalysisService emotionAnalysisService;
    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private ContentServiceImpl contentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 加载敏感词库（DFA 静态状态）
        SensitiveWord sw = new SensitiveWord();
        sw.setWord("傻逼");
        SensitiveWordUtils.loadSensitiveWordsFromDatabase(List.of(sw));
        when(emotionAnalysisService.analyzeContentEmotion(any(), any())).thenReturn(1);
        when(emotionAnalysisService.calculateEmotionScore(any(), any())).thenReturn(0.6);
    }

    private Content buildContent(String text) {
        Content content = new Content();
        content.setContentText(text);
        content.setAnonymousId("u1");
        return content;
    }

    private RiskAssessmentService.AssessmentResult buildAssessment(String action,
                                                                   RiskAssessmentService.RiskLevel level,
                                                                   int score) {
        RiskAssessmentService.AssessmentResult result = new RiskAssessmentService.AssessmentResult();
        result.setAction(action);
        result.setRiskLevel(level);
        result.setRiskScore(score);
        result.setRiskKeywords(List.of());
        return result;
    }

    @Test
    void shouldRejectPublishWhenContainsSensitiveWord() {
        Content content = buildContent("你这个傻逼，真让人无语");

        ExceptionUtils.BusinessException ex = assertThrows(
                ExceptionUtils.BusinessException.class,
                () -> contentService.publishContent(content));

        assertEquals(400, ex.getCode());
        verify(contentMapper, never()).insertContent(any());
    }

    @Test
    void shouldPublishLowRiskContent() {
        Content content = buildContent("今天天气不错，心情很好");
        when(riskAssessmentService.assessRisk(content.getContentText()))
                .thenReturn(buildAssessment("PUBLISH", RiskAssessmentService.RiskLevel.NONE, 0));
        when(contentMapper.insertContent(any(Content.class))).thenReturn(1);

        Result<Content> result = contentService.publishContent(content);

        assertEquals(200, result.getCode());
        assertEquals("发布成功", result.getMessage());
        assertEquals(1, content.getContentStatus());
        // 发布后主动失效首页列表缓存
        verify(redisCacheService).deleteByPattern("content:list:*");
    }

    @Test
    void shouldMarkHighRiskContentPending() {
        Content content = buildContent("我最近压力很大，感觉快撑不住了");
        when(riskAssessmentService.assessRisk(content.getContentText()))
                .thenReturn(buildAssessment("REJECT", RiskAssessmentService.RiskLevel.HIGH, 8));
        when(contentMapper.insertContent(any(Content.class))).thenReturn(1);

        Result<Content> result = contentService.publishContent(content);

        assertEquals(200, result.getCode());
        assertEquals("内容已提交，正在审核中", result.getMessage());
        assertEquals(0, content.getContentStatus());
        verify(contentMapper).insertContent(content);
    }

    @Test
    void shouldRequireContentText() {
        Content content = buildContent("  ");
        ExceptionUtils.BusinessException ex = assertThrows(
                ExceptionUtils.BusinessException.class,
                () -> contentService.publishContent(content));
        assertEquals(400, ex.getCode());
    }

    @Test
    void shouldRequireAnonymousId() {
        Content content = buildContent("正常内容");
        content.setAnonymousId("");
        ExceptionUtils.BusinessException ex = assertThrows(
                ExceptionUtils.BusinessException.class,
                () -> contentService.publishContent(content));
        assertEquals(400, ex.getCode());
    }
}
