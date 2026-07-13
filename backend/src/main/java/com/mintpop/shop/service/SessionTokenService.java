package com.mintpop.shop.service;

import com.mintpop.shop.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * 自签会话 token 服务（BFF）：HS256 JWT，claims 只含 userid、不含 sub。
 * 会话控制权归本产品：换密钥即全员下线，TTL 自定。
 */
@Service
public class SessionTokenService {

    private final SecretKey key;
    private final Duration ttl;

    public SessionTokenService(AuthProperties properties) {
        String secret = properties.getSessionSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.auth.session-secret 未配置或不足 32 字节，请在 jar 外 config/application.yml 提供");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.getSessionTtl();
    }

    /** 签发会话 token：subject 即内部 userid */
    public String issue(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析会话 token：签名/过期/格式任一不符返回空（视为未登录，不抛异常） */
    public Optional<Long> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
