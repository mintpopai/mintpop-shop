package com.mintpop.shop.security;

import com.mintpop.shop.config.AuthProperties;
import com.mintpop.shop.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;

/**
 * 会话认证过滤器：解析自签会话 Cookie（JWT，内含 userid），有效则写入 SecurityContext。
 * 无效/缺失静默放行（是否放行由授权规则裁决），全程不产生服务端 session。
 * 注意：不加 @Component——由 SecurityConfig 显式装入安全链，避免被 Servlet 容器再注册一次。
 */
@RequiredArgsConstructor
public class SessionCookieAuthFilter extends OncePerRequestFilter {

    private final SessionTokenService sessionTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, AuthProperties.SESSION_COOKIE_NAME);
        if (cookie != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            sessionTokenService.parse(cookie.getValue()).ifPresent(userId ->
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        }
        filterChain.doFilter(request, response);
    }
}
