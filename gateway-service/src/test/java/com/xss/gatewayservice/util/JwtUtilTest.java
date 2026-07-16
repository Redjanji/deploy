package com.xss.gatewayservice.util;

import com.xss.gatewayservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private JwtProperties jwtProperties;

    private static final String SECRET = "my-test-secret-key-for-jwt-signing";
    private static final String APP_ID = "test-app-123";

    private PublicKey rsaPublicKey;
    private PrivateKey rsaPrivateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        rsaPrivateKey = keyPair.getPrivate();
        rsaPublicKey = keyPair.getPublic();

        String secretBase64 = Base64.getEncoder().encodeToString(SECRET.getBytes(StandardCharsets.UTF_8));
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder().encodeToString(rsaPublicKey.getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        jwtProperties = new JwtProperties();
        jwtProperties.setSecretBase64(secretBase64);
        jwtProperties.setUserPublicKey(publicKeyPem);

        jwtUtil = new JwtUtil(jwtProperties);
    }

    @Test
    @DisplayName("generateToken: 成功生成HMAC签名的Token")
    void generateToken_shouldGenerateValidToken() {
        String token = jwtUtil.generateToken(APP_ID);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    @DisplayName("parseClaims: 有效HMAC token正确解析")
    void parseClaims_validHmacToken_shouldParseSuccessfully() {
        String token = jwtUtil.generateToken(APP_ID);

        Claims claims = jwtUtil.parseClaims(token);

        assertNotNull(claims);
        assertEquals(APP_ID, claims.getSubject());
        assertEquals("app", claims.get("type"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("parseClaims: 有效RSA token正确解析")
    void parseClaims_validRsaToken_shouldParseSuccessfully() {
        String userId = "user-456";
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject(userId)
                .claim("type", "user")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 2 * 60 * 60 * 1000))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .compact();

        Claims claims = jwtUtil.parseClaims(token);

        assertNotNull(claims);
        assertEquals(userId, claims.getSubject());
        assertEquals("user", claims.get("type"));
    }

    @Test
    @DisplayName("validateTokenAndGetAppId: 有效token返回正确subject")
    void validateTokenAndGetAppId_validToken_shouldReturnSubject() {
        String token = jwtUtil.generateToken(APP_ID);

        String subject = jwtUtil.validateTokenAndGetAppId(token);

        assertEquals(APP_ID, subject);
    }

    @Test
    @DisplayName("parseClaims: 过期token抛出异常")
    void parseClaims_expiredToken_shouldThrowException() {
        long pastTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
        byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");

        String expiredToken = Jwts.builder()
                .subject(APP_ID)
                .claim("type", "app")
                .issuedAt(new Date(pastTime))
                .expiration(new Date(pastTime + 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThrows(Exception.class, () -> jwtUtil.parseClaims(expiredToken));
    }

    @Test
    @DisplayName("parseClaims: 无效签名token抛出异常")
    void parseClaims_invalidSignatureToken_shouldThrowException() {
        String wrongSecret = "wrong-secret-key-for-testing-purposes";
        byte[] keyBytes = wrongSecret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");

        String badToken = Jwts.builder()
                .subject(APP_ID)
                .claim("type", "app")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThrows(SignatureException.class, () -> jwtUtil.parseClaims(badToken));
    }

    @Test
    @DisplayName("parseClaims: 格式错误的token抛出异常")
    void parseClaims_malformedToken_shouldThrowException() {
        assertThrows(Exception.class, () -> jwtUtil.parseClaims("invalid.token.here"));
    }

    @Test
    @DisplayName("parseClaims: 空token抛出异常")
    void parseClaims_emptyToken_shouldThrowException() {
        assertThrows(Exception.class, () -> jwtUtil.parseClaims(""));
    }

    @Test
    @DisplayName("parseClaims: null token抛出异常")
    void parseClaims_nullToken_shouldThrowException() {
        assertThrows(Exception.class, () -> jwtUtil.parseClaims(null));
    }

    @Test
    @DisplayName("getUserId: RSA token中正确提取userId")
    void getUserId_fromRsaToken_shouldReturnUserId() {
        String userId = "user-789";
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject(userId)
                .claim("type", "user")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 2 * 60 * 60 * 1000))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .compact();

        Claims claims = jwtUtil.parseClaims(token);
        String extractedUserId = claims.getSubject();

        assertEquals(userId, extractedUserId);
        assertEquals("user", claims.get("type"));
    }

    @Test
    @DisplayName("generateToken: token包含正确的过期时间")
    void generateToken_shouldHaveCorrectExpiration() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateToken(APP_ID);
        long after = System.currentTimeMillis();

        Claims claims = jwtUtil.parseClaims(token);
        long expiration = claims.getExpiration().getTime();
        long expectedExpiration = before + 2 * 60 * 60 * 1000;

        assertTrue(expiration >= before + 2 * 60 * 60 * 1000 - 1000);
        assertTrue(expiration <= after + 2 * 60 * 60 * 1000 + 1000);
    }

    @Test
    @DisplayName("generateToken: 不同appId生成不同token")
    void generateToken_differentAppIds_shouldProduceDifferentTokens() {
        String token1 = jwtUtil.generateToken("app-1");
        String token2 = jwtUtil.generateToken("app-2");

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("parseClaims: RSA token错误密钥抛出异常")
    void parseClaims_rsaTokenWithWrongKey_shouldThrowException() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        PrivateKey wrongPrivateKey = keyGen.generateKeyPair().getPrivate();

        String badToken = Jwts.builder()
                .subject("hacker")
                .claim("type", "user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongPrivateKey, Jwts.SIG.RS256)
                .compact();

        assertThrows(SignatureException.class, () -> jwtUtil.parseClaims(badToken));
    }

    @Test
    @DisplayName("RSA公钥未配置时解析RS256 token抛出异常")
    void parseClaims_rsaWithoutPublicKey_shouldThrowException() {
        JwtProperties propsWithoutRsa = new JwtProperties();
        propsWithoutRsa.setSecretBase64(Base64.getEncoder().encodeToString(SECRET.getBytes(StandardCharsets.UTF_8)));
        propsWithoutRsa.setUserPublicKey(null);

        JwtUtil jwtUtilWithoutRsa = new JwtUtil(propsWithoutRsa);

        String rsaToken = Jwts.builder()
                .subject("test")
                .claim("type", "user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .compact();

        assertThrows(IllegalStateException.class, () -> jwtUtilWithoutRsa.parseClaims(rsaToken));
    }
}
