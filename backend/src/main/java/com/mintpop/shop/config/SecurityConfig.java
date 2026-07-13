package com.mintpop.shop.config;

import com.mintpop.shop.security.CookieOAuth2AuthorizationRequestRepository;
import com.mintpop.shop.security.OidcLoginSuccessHandler;
import com.mintpop.shop.security.SessionCookieAuthFilter;
import com.mintpop.shop.service.SessionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（BFF）：OIDC 只管登录握手，日常鉴权走自签会话 Cookie，全程无服务端 session。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final SessionTokenService sessionTokenService;
    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 会话 Cookie 为 SameSite=Lax，浏览器已拦截跨站写请求，无需 CSRF token
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 登出走 AuthController 的 GET /auth/logout，禁用框架默认 POST /logout
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/me", "/api/orders", "/api/orders/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(login -> login
                        // 握手中间态存 Cookie（只对本浏览器有效），不落服务端 session
                        .authorizationEndpoint(ae -> ae.authorizationRequestRepository(
                                new CookieOAuth2AuthorizationRequestRepository()))
                        // 回调路径按统一账号接入规范固定为 /auth/callback
                        .redirectionEndpoint(re -> re.baseUri("/auth/callback"))
                        .successHandler(oidcLoginSuccessHandler)
                        // 握手失败（用户取消/state 不符等）：回前端带标记，由前端中文提示
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(authProperties.getFrontendBaseUrl() + "?login_error=1")))
                // 未登录访问受保护接口：返回 401（规范允许鉴权中间件用原生状态码），不重定向登录页
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new SessionCookieAuthFilter(sessionTokenService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
