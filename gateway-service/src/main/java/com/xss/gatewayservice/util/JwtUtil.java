package com.xss.gatewayservice.util;

import com.xss.gatewayservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRATION_MS = 2 * 60 * 60 * 1000;
    private static final String ALG_HMAC = "HS256";
    private static final String ALG_RSA = "RS256";

    private final SecretKeySpec hmacKey;
    private final PublicKey rsaPublicKey;

    public JwtUtil(JwtProperties props) {
        byte[] keyBytes = Base64.getDecoder().decode(props.getSecretBase64());
        this.hmacKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.rsaPublicKey = loadPublicKey(props.getUserPublicKey());
    }

    public String generateToken(String appId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);
        return Jwts.builder()
                .subject(appId)
                .claim("type", "app")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(hmacKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .keyLocator(new Locator<Key>() {
                    @Override
                    public Key locate(Header header) {
                        if (!(header instanceof JwsHeader jws)) {
                            throw new IllegalArgumentException("Unsupported JWT: not a JWS");
                        }
                        String alg = jws.getAlgorithm();
                        if (ALG_HMAC.equals(alg)) {
                            return hmacKey;
                        }
                        if (ALG_RSA.equals(alg)) {
                            if (rsaPublicKey == null) {
                                throw new IllegalStateException("RSA public key not configured");
                            }
                            return rsaPublicKey;
                        }
                        throw new IllegalArgumentException("Unsupported alg: " + alg);
                    }
                })
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String validateTokenAndGetAppId(String token) {
        return parseClaims(token).getSubject();
    }

    private static PublicKey loadPublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(cleaned);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load jwt.user-public-key", e);
        }
    }
}
