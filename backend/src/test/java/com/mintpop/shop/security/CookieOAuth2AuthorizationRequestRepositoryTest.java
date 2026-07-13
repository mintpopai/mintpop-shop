package com.mintpop.shop.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository();

    private OAuth2AuthorizationRequest sampleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://auth.example.com/oidc/auth")
                .clientId("client-1")
                .redirectUri("http://localhost:5173/auth/callback")
                .scopes(Set.of("openid", "profile", "email"))
                .state("state-abc")
                .additionalParameters(Map.of("nonce", "nonce-hash"))
                .attributes(attrs -> {
                    attrs.put("registration_id", "logto");
                    attrs.put("nonce", "nonce-raw");
                    attrs.put("code_verifier", "pkce-verifier-xyz");
                })
                .build();
    }

    /** 从响应的 Set-Cookie 头取 Cookie 值 */
    private String cookieValue(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull().startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "=");
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    @Test
    @DisplayName("save 后可从 Cookie load 回同样的授权请求（state/nonce/PKCE 属性齐全）")
    void saveThenLoadRoundTrip() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue(saveResponse)));
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-abc");
        assertThat(loaded.getClientId()).isEqualTo("client-1");
        assertThat(loaded.getRedirectUri()).isEqualTo("http://localhost:5173/auth/callback");
        assertThat(loaded.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(loaded.getAdditionalParameters()).containsEntry("nonce", "nonce-hash");
        assertThat(loaded.<String>getAttribute("registration_id")).isEqualTo("logto");
        assertThat(loaded.<String>getAttribute("nonce")).isEqualTo("nonce-raw");
        assertThat(loaded.<String>getAttribute("code_verifier")).isEqualTo("pkce-verifier-xyz");
        assertThat(loaded.getAuthorizationUri()).isEqualTo("https://auth.example.com/oidc/auth");
    }

    @Test
    @DisplayName("无 Cookie 时 load 返回 null")
    void loadWithoutCookieReturnsNull() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("Cookie 内容损坏时 load 返回 null（重新走登录，不抛异常）")
    void corruptedCookieReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, "garbage"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    @DisplayName("remove 返回中间态并让 Cookie 过期")
    void removeReturnsAndExpires() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), saveResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue(saveResponse)));
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-abc");
        assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }
}
