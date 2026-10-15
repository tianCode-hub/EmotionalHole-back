package com.myk.emotionalHole.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 百度文本内容审核工具类
 * 调用百度智能云"文本审核"API（text_censor）进行确定性机器过滤，作为 AI 审核的前置兜底层
 * 使用 client_credentials 模式获取 access_token（有效期30天，内存缓存并提前1天刷新）
 * 不可用或未配置时返回 PASS（跳过），由上层 AI 审核继续兜底
 */
@Component
public class BaiduTextAuditUtils {

    private static final Logger logger = LoggerFactory.getLogger(BaiduTextAuditUtils.class);

    public enum AuditResult {
        PASS(1, "通过"),
        SUSPECT(2, "疑似违规"),
        REJECT(3, "确认违规");

        private final int code;
        private final String desc;

        AuditResult(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RestTemplate restTemplate;

    @Value("${baidu-audit.enabled:true}")
    private boolean enabled;

    @Value("${baidu-audit.api-key:}")
    private String apiKey;

    @Value("${baidu-audit.secret-key:}")
    private String secretKey;

    @Value("${baidu-audit.token-url:https://aip.baidubce.com/oauth/2.0/token}")
    private String tokenUrl;

    @Value("${baidu-audit.text-censor-url:https://aip.baidubce.com/rest/2.0/solution/v1/text_censor/v2/user_defined}")
    private String textCensorUrl;

    /** access_token 内存缓存 */
    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    /** 文本审核入口：机器过滤层，返回 PASS/SUSPECT/REJECT */
    public AuditResult auditText(String content) {
        if (!enabled) {
            return AuditResult.PASS;
        }
        if (content == null || content.trim().isEmpty()) {
            return AuditResult.PASS;
        }
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            logger.info("百度文本审核未配置，跳过机器过滤");
            return AuditResult.PASS;
        }

        try {
            String accessToken = getAccessToken();

            // 构造表单请求
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("text", content);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    textCensorUrl + "?access_token=" + accessToken, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode node = objectMapper.readTree(response.getBody());
                String conclusion = node.path("conclusion").asText("");
                switch (conclusion) {
                    case "不合规":
                        return AuditResult.REJECT;
                    case "疑似":
                        return AuditResult.SUSPECT;
                    default:
                        return AuditResult.PASS;
                }
            }
        } catch (Exception e) {
            logger.warn("百度文本审核调用失败，跳过机器过滤: {}", e.getMessage());
        }
        return AuditResult.PASS;
    }

    /** 获取 access_token：缓存有效期内直接复用，快过期时重新获取 */
    private String getAccessToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }

        String url = tokenUrl + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        JsonNode node = objectMapper.readTree(response.getBody());
        String token = node.path("access_token").asText("");
        if (token.isEmpty()) {
            throw new RuntimeException("获取百度access_token失败: " + response.getBody());
        }

        // token 有效期30天（2592000s），提前1天刷新，避免过期失效
        long expiresIn = node.path("expires_in").asLong(2592000L);
        cachedToken = token;
        tokenExpireAt = System.currentTimeMillis() + (expiresIn - 86400) * 1000;
        return token;
    }
}
