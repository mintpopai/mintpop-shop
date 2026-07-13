package com.mintpop.shop.controller;

import com.mintpop.shop.config.AuthProperties;
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
                .standaloneSetup(new AuthController(clientRegistrationRepository, new AuthProperties()))
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
