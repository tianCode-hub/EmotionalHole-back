package com.myk.emotionalHole.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myk.emotionalHole.entity.SensitiveWord;
import com.myk.emotionalHole.entity.User;
import com.myk.emotionalHole.mapper.UserMapper;
import com.myk.emotionalHole.service.SensitiveWordService;
import com.myk.emotionalHole.util.JwtUtils;
import com.myk.emotionalHole.util.PageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内容发布接口集成测试
 *
 * 覆盖：内容发布成功、敏感词拒绝、AI/百度审核不可用降级、分页参数上限、@Valid 参数校验
 * 说明：本用例连接本机 MySQL(emotional_hole)，依赖初始化数据；
 * 通过 @SpringBootTest(properties=...) 将 baidu-audit 关闭、ai.api-key 置空，避免外部 API 调用，
 * 验证"审核链路不可用时发布仍能正常降级"。
 */
@SpringBootTest(properties = {
        "ai.api-key=",
        "baidu-audit.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@Rollback
class ContentPublishIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    /** 从DB取一个正常状态用户生成访问令牌 */
    private String buildAccessToken() {
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .ne(User::getUserStatus, 3)
                .last("LIMIT 1"));
        if (users.isEmpty()) {
            return null;
        }
        return JwtUtils.generateAccessToken(users.get(0).getOpenid());
    }

    private String token;

    @BeforeEach
    void setUp() {
        token = buildAccessToken();
    }

    @Test
    @DisplayName("发布正常内容：AI/百度审核不可用时降级，发布仍成功")
    void publishContent_successWithAuditDegraded() throws Exception {
        assumeTrue(token != null, "数据库无可用用户，跳过发布用例");

        mockMvc.perform(post("/content/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"今天阳光明媚，和小伙伴一起散步，非常开心\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.contentText").value("今天阳光明媚，和小伙伴一起散步，非常开心"))
                .andExpect(jsonPath("$.data.contentStatus").isNumber());
    }

    @Test
    @DisplayName("发布含本地敏感词内容：本地敏感词拦截，拒绝发布")
    void publishContent_sensitiveWordRejected() throws Exception {
        assumeTrue(token != null, "数据库无可用用户，跳过发布用例");

        List<SensitiveWord> words = sensitiveWordService.getSensitiveWordsByType(1);
        assumeTrue(!words.isEmpty(), "数据库无类型1敏感词，跳过敏感词拦截用例");

        String word = words.get(0).getWord();
        mockMvc.perform(post("/content/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"这是一条包含" + word + "的测试内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("敏感词")));
    }

    @Test
    @DisplayName("发布空内容：@Valid 校验拦截，返回400")
    void publishContent_blankTextRejectedByValidation() throws Exception {
        assumeTrue(token != null, "数据库无可用用户，跳过发布用例");

        mockMvc.perform(post("/content/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("管理员登录空账号：@Valid 校验拦截，返回400")
    void adminLogin_blankParamsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminAccount\":\"\",\"adminPassword\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("分页参数边界：pageSize 超限被 clamp 到100，非法 page 归一为1")
    void pagination_pageSizeClamped() throws Exception {
        // 工具类边界断言
        assertThat(PageUtils.clampPageSize(999)).isEqualTo(100);
        assertThat(PageUtils.clampPageSize(0)).isEqualTo(1);
        assertThat(PageUtils.clampPageNum(-5)).isEqualTo(1);

        // 接口层面：超大 pageSize 不报错，正常返回
        mockMvc.perform(get("/content/list")
                        .param("page", "-5")
                        .param("pageSize", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
