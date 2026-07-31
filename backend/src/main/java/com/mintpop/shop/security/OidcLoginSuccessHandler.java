package com.mintpop.shop.security;

import com.mintpop.shop.config.AuthProperties;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.service.SessionTokenService;
import com.mintpop.shop.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * OIDC 登录成功处理：sub 在此边界换成 userid（建号/刷新 email），
 * 自签会话 Cookie 下发浏览器，302 回前端。账号中心 token 用完即弃，不下发、不落库。
 */
@Component
@RequiredArgsConstructor
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final SessionTokenService sessionTokenService;
    private final AuthProperties authProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        ShopUser user = userService.syncOnLogin(
                oidcUser.getSubject(), oidcUser.getEmail(), oidcUser.getFullName(), oidcUser.getPicture(),
                preferredLocale(request));

        ResponseCookie cookie = ResponseCookie.from(
                        AuthProperties.SESSION_COOKIE_NAME, sessionTokenService.issue(user.getId()))
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(authProperties.getSessionTtl())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.sendRedirect(authProperties.getFrontendBaseUrl());
    }

    /** 首次登录的语言种子：按 Accept-Language 判定，语言子标签为 en 视为英文（与 I18nUtil 同规则） */
    private String preferredLocale(HttpServletRequest request) {
        Locale locale = request.getLocale();
        return locale != null && "en".equals(locale.getLanguage()) ? "en-US" : "zh-CN";
    }
}
