package com.mintpop.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 自签会话配置（app.auth.*）：密钥来自 jar 外 config/application.yml，不入库。
 */
@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 会话 Cookie 名 */
    public static final String SESSION_COOKIE_NAME = "mp_session";

    /** 自签会话 JWT 的 HS256 密钥，至少 32 字节（openssl rand -base64 48 生成） */
    private String sessionSecret;
    /** 会话有效期，默认 7 天 */
    private Duration sessionTtl = Duration.ofDays(7);
    /** 登录/登出完成后回跳的前端地址（同源部署用默认 /；本地开发经 Vite 代理也是 /） */
    private String frontendBaseUrl = "/";
}
