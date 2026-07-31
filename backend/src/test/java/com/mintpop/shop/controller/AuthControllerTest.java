package com.mintpop.shop.controller;

import com.mintpop.shop.config.AuthProperties;
import com.mintpop.shop.security.PostLoginRedirectCookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private ClientRegistrationRepository clientRegistrationRepository;

    private ClientRegistration logtoRegistration(Map<String, Object> providerMetadata) {
        return ClientRegistration.withRegistrationId("logto")
                .clientId("client-1")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/auth/callback")
                .authorizationUri("https://auth.example.com/oidc/auth")
                .tokenUri("https://auth.example.com/oidc/token")
                .providerConfigurationMetadata(providerMetadata)
                .build();
    }

    @BeforeEach
    void setUp() {
        clientRegistrationRepository = mock(ClientRegistrationRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        clientRegistrationRepository, new AuthProperties(), new PostLoginRedirectCookie()))
                .build();
    }

    @Test
    @DisplayName("GET /auth/login 跳转到 OIDC 授权发起端点")
    void loginRedirectsToAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"));
    }

    @Test
    @DisplayName("带合法 redirect 参数时下发回跳路径 Cookie，10 分钟有效期、根路径、值可还原为原路径")
    void loginWithValidRedirectIssuesCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/login").param("redirect", "/orders/mintpopshop_x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(PostLoginRedirectCookie.COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=600")
                .contains("Path=/");
        assertThat(setCookie).doesNotContain(PostLoginRedirectCookie.COOKIE_NAME + "=;");

        String cookieValue = setCookie.substring(
                setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("/orders/mintpopshop_x");
    }

    @Test
    @DisplayName("带非法 redirect 参数（开放重定向）时清掉回跳路径 Cookie（不继承任何历史值）")
    void loginWithInvalidRedirectClearsCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/login").param("redirect", "//evil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith(PostLoginRedirectCookie.COOKIE_NAME + "=") && h.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("redirect 参数经 URL 编码后（解码后才校验）仍被判非法并清掉 Cookie")
    void loginWithEncodedInvalidRedirectClearsCookie() throws Exception {
        // %2F%2Fevil.com 解码后为 //evil.com；直接走原始查询串，覆盖「解码后再校验」这一步，
        // 不能像其余用例那样用 .param() 直接喂解码后的值（那会跳过 Spring 的查询串解码阶段）
        MvcResult result = mockMvc.perform(get("/auth/login?redirect=%2F%2Fevil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith(PostLoginRedirectCookie.COOKIE_NAME + "=") && h.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("不带 redirect 参数时也清掉回跳路径 Cookie（不继承任何历史值）")
    void loginWithoutRedirectParamClearsCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith(PostLoginRedirectCookie.COOKIE_NAME + "=") && h.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("GET /auth/logout 清会话 Cookie 并跳转账号中心 end-session")
    void logoutClearsCookieAndRedirectsToEndSession() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId("logto")).thenReturn(
                logtoRegistration(Map.of("end_session_endpoint", "https://auth.example.com/oidc/session/end")));

        MvcResult result = mockMvc.perform(get("/auth/logout"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(AuthProperties.SESSION_COOKIE_NAME + "=").contains("Max-Age=0");
        String redirected = result.getResponse().getRedirectedUrl();
        assertThat(redirected)
                .startsWith("https://auth.example.com/oidc/session/end")
                .contains("client_id=client-1")
                .contains("post_logout_redirect_uri=")
                .contains("/auth/logout/callback");
    }

    @Test
    @DisplayName("账号中心未暴露 end-session 时，登出降级为只清 Cookie 回前端")
    void logoutWithoutEndSessionFallsBackToFrontend() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId("logto")).thenReturn(
                logtoRegistration(Map.of()));

        mockMvc.perform(get("/auth/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("GET /auth/logout/callback 回前端首页")
    void logoutCallbackRedirectsToFrontend() throws Exception {
        mockMvc.perform(get("/auth/logout/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
