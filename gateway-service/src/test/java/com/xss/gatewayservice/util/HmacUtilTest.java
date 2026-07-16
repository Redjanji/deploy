package com.xss.gatewayservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HmacUtil 单元测试")
class HmacUtilTest {

    @Test
    @DisplayName("hmacSha256: 正确生成HMAC-SHA256签名")
    void hmacSha256_shouldGenerateCorrectSignature() {
        String data = "test-data";
        String secret = "test-secret";

        String signature = HmacUtil.hmacSha256(data, secret);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertEquals(44, signature.length());
    }

    @Test
    @DisplayName("hmacSha256: 相同参数相同密钥生成相同签名")
    void hmacSha256_sameInputSameSecret_shouldProduceSameSignature() {
        String data = "app1:1234567890:nonce123";
        String secret = "my-secret-key";

        String sig1 = HmacUtil.hmacSha256(data, secret);
        String sig2 = HmacUtil.hmacSha256(data, secret);

        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("hmacSha256: 不同密钥生成不同签名")
    void hmacSha256_differentSecrets_shouldProduceDifferentSignatures() {
        String data = "test-data";
        String secret1 = "secret-1";
        String secret2 = "secret-2";

        String sig1 = HmacUtil.hmacSha256(data, secret1);
        String sig2 = HmacUtil.hmacSha256(data, secret2);

        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("hmacSha256: 不同数据生成不同签名")
    void hmacSha256_differentData_shouldProduceDifferentSignatures() {
        String data1 = "data-1";
        String data2 = "data-2";
        String secret = "same-secret";

        String sig1 = HmacUtil.hmacSha256(data1, secret);
        String sig2 = HmacUtil.hmacSha256(data2, secret);

        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("constantTimeEquals: 相同字符串返回true")
    void constantTimeEquals_equalStrings_shouldReturnTrue() {
        String a = "abcdef123456";
        String b = "abcdef123456";

        assertTrue(HmacUtil.constantTimeEquals(a, b));
    }

    @Test
    @DisplayName("constantTimeEquals: 不同字符串返回false")
    void constantTimeEquals_differentStrings_shouldReturnFalse() {
        String a = "abcdef123456";
        String b = "abcdef123457";

        assertFalse(HmacUtil.constantTimeEquals(a, b));
    }

    @Test
    @DisplayName("constantTimeEquals: 空字符串相等")
    void constantTimeEquals_emptyStrings_shouldReturnTrue() {
        assertTrue(HmacUtil.constantTimeEquals("", ""));
    }

    @Test
    @DisplayName("constantTimeEquals: 长度不同返回false")
    void constantTimeEquals_differentLength_shouldReturnFalse() {
        String a = "short";
        String b = "longer-string";

        assertFalse(HmacUtil.constantTimeEquals(a, b));
    }

    @Test
    @DisplayName("验证签名: 合法签名验证通过")
    void verifySignature_validSignature_shouldPass() {
        String data = "appId:timestamp:nonce";
        String secret = "app-secret";

        String signature = HmacUtil.hmacSha256(data, secret);

        assertTrue(HmacUtil.constantTimeEquals(signature, HmacUtil.hmacSha256(data, secret)));
    }

    @Test
    @DisplayName("验证签名: 错误签名验证失败")
    void verifySignature_invalidSignature_shouldFail() {
        String data = "appId:timestamp:nonce";
        String secret = "app-secret";

        String signature = HmacUtil.hmacSha256(data, secret);
        String wrongSignature = HmacUtil.hmacSha256(data, "wrong-secret");

        assertFalse(HmacUtil.constantTimeEquals(signature, wrongSignature));
    }

    @Test
    @DisplayName("hmacSha256: 空字符串数据也能生成签名")
    void hmacSha256_emptyData_shouldGenerateSignature() {
        String signature = HmacUtil.hmacSha256("", "secret");

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("hmacSha256: 中文数据正确生成签名")
    void hmacSha256_chineseData_shouldGenerateSignature() {
        String data = "测试数据";
        String secret = "密钥";

        String sig1 = HmacUtil.hmacSha256(data, secret);
        String sig2 = HmacUtil.hmacSha256(data, secret);

        assertNotNull(sig1);
        assertEquals(sig1, sig2);
    }
}
