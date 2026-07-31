package com.mintpop.shop.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 登录后回跳路径的短命 Cookie（用完即弃）：把「合法站内路径」的校验与 Cookie 读写收口在一处，
 * 供 AuthController（写入）与 OidcLoginSuccessHandler（读取）共用，避免开放重定向校验散落两处漂移。
 * Cookie 是客户端可改的，故写入时校验一遍、读取时（真正把用户送走前）必须再校验一遍。
 */
@Component
public class PostLoginRedirectCookie {

    public static final String COOKIE_NAME = "mp_post_login";
    /** 存活时间与握手中间态 Cookie（mp_oidc_flow）保持一致：够完成一次登录即可 */
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_LENGTH = 512;
    /** 含 \r \n 等控制字符：防响应头/Cookie 注入 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    /**
     * 判定是否为合法的站内相对路径：非空白、不超长、以单个 / 开头、
     * 不以 // 开头（协议相对 URL）、不含反斜杠、不含 :// 、不含控制字符、不含 /../ 路径段
     * 且不以 /.. 结尾（同源不构成开放重定向，但 Tomcat 在 use-relative-redirects=false 且
     * ForwardedHeaderFilter 未介入时，路径起始处的 /../ 会让 Response.normalize 直接抛异常）。
     */
    public boolean isValidPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.length() > MAX_LENGTH) {
            return false;
        }
        if (!path.startsWith("/") || path.startsWith("//")) {
            return false;
        }
        if (path.contains("\\") || path.contains("://")) {
            return false;
        }
        if (path.contains("/../") || path.endsWith("/..")) {
            return false;
        }
        return !CONTROL_CHARS.matcher(path).find();
    }

    /** 写入回跳路径 Cookie（调用方需先用 isValidPath 校验，本方法只负责编码写 Cookie） */
    public void write(HttpServletRequest request, HttpServletResponse response, String path) {
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.getBytes(StandardCharsets.UTF_8));
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, value, TTL).toString());
    }

    /** 读取并再次校验 Cookie 中的回跳路径；不存在/损坏/不合法一律返回 null（静默回退，不报错、不回显） */
    public String readValid(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        String path;
        try {
            path = new String(Base64.getUrlDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return isValidPath(path) ? path : null;
    }

    /** 清掉 Cookie：登录成功流程读取一次后无论走哪条分支都要调用，用完即弃 */
    public void expire(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, "", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
