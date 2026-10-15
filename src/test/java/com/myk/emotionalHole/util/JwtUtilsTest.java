package com.myk.emotionalHole.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 双令牌机制单元测试
 */
class JwtUtilsTest {

    private static final String SECRET = "test_jwt_secret_key_at_least_32_chars_0123456789";
    private static final String OPENID = "openid_10001";

    @BeforeEach
    void setUp() {
        JwtUtils jwt = new JwtUtils();
        jwt.setSecretKey(SECRET);
        jwt.setAccessTokenExpiration(7200000L);
        jwt.setRefreshTokenExpiration(604800000L);
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        String token = JwtUtils.generateAccessToken(OPENID);
        assertNotNull(token);
        assertTrue(JwtUtils.validateToken(token));
    }

    @Test
    void shouldExtractOpenidFromToken() {
        String token = JwtUtils.generateAccessToken(OPENID);
        assertEquals(OPENID, JwtUtils.extractOpenid(token));
    }

    @Test
    void shouldExtractTokenType() {
        String access = JwtUtils.generateAccessToken(OPENID);
        String refresh = JwtUtils.generateRefreshToken(OPENID);
        assertEquals("access", JwtUtils.extractTokenType(access));
        assertEquals("refresh", JwtUtils.extractTokenType(refresh));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(JwtUtils.validateToken("invalid.token.value"));
        assertFalse(JwtUtils.validateToken(null));
    }

    @Test
    void shouldRejectExpiredToken() {
        // 构造过期时间（负值 => 立即过期）
        JwtUtils jwt = new JwtUtils();
        jwt.setSecretKey(SECRET);
        jwt.setAccessTokenExpiration(-1000L);
        jwt.setRefreshTokenExpiration(-1000L);

        String expired = JwtUtils.generateAccessToken(OPENID);
        assertFalse(JwtUtils.validateToken(expired));
        assertNull(JwtUtils.refreshAccessToken(expired));
    }

    @Test
    void shouldRefreshWithValidRefreshToken() {
        String refresh = JwtUtils.generateRefreshToken(OPENID);
        String newAccess = JwtUtils.refreshAccessToken(refresh);
        assertNotNull(newAccess);
        assertEquals("access", JwtUtils.extractTokenType(newAccess));
        assertEquals(OPENID, JwtUtils.extractOpenid(newAccess));
    }

    @Test
    void shouldNotRefreshWithAccessToken() {
        String access = JwtUtils.generateAccessToken(OPENID);
        assertNull(JwtUtils.refreshAccessToken(access));
    }
}
