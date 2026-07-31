package com.mintpop.shop.controller;

import com.mintpop.shop.config.AuthProperties;
import com.mintpop.shop.security.PostLoginRedirectCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * 登录/登出入口（回调地址按统一账号接入规范固定为 /auth/callback、/auth/logout/callback）。
 * OIDC 回调 /auth/callback 由 Spring Security 过滤器处理，不在本 controller。
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final String REGISTRATION_ID = "logto";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final AuthProperties authProperties;
    private final PostLoginRedirectCookie postLoginRedirectCookie;

    /**
     * 登录入口：跳到 Spring Security 的授权发起端点（授权码 + PKCE）。
     * 可选 redirect 参数携带登录前原路径（如深链邮件里的 /orders/xxx），校验通过写成短命 Cookie，
     * 登录成功后由 OidcLoginSuccessHandler 读取并回跳；非法值静默丢弃，不影响登录本身。
     */
    @GetMapping("/auth/login")
    public void login(HttpServletRequest request, HttpServletResponse response,
                      @RequestParam(name = "redirect", required = false) String redirect) throws IOException {
        if (redirect != null && postLoginRedirectCookie.isValidPath(redirect)) {
            postLoginRedirectCookie.write(request, response, redirect);
        }
        response.sendRedirect("/oauth2/authorization/" + REGISTRATION_ID);
    }

    /**
     * 登出：清本地会话 Cookie，再跳账号中心 end-session 单点登出（无 end-session 则直接回前端）
     * （已知取舍：GET 形态可被跨站强制触发登出，风险仅骚扰级，已接受）
     */
    @GetMapping("/auth/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ResponseCookie expired = ResponseCookie.from(AuthProperties.SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());

        ClientRegistration logto = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        Object endSession = logto == null ? null
                : logto.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
        if (endSession == null) {
            response.sendRedirect(authProperties.getFrontendBaseUrl());
            return;
        }
        String postLogoutRedirect = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/auth/logout/callback").build().toUriString();
        String target = UriComponentsBuilder.fromUriString(endSession.toString())
                .queryParam("client_id", logto.getClientId())
                .queryParam("post_logout_redirect_uri", postLogoutRedirect)
                .encode()
                .build()
                .toUriString();
        response.sendRedirect(target);
    }

    /** 登出回调：账号中心清完会话后回到这里，再回前端 */
    @GetMapping("/auth/logout/callback")
    public void logoutCallback(HttpServletResponse response) throws IOException {
        response.sendRedirect(authProperties.getFrontendBaseUrl());
    }
}
