package com.mintpop.shop.config;

import com.mintpop.shop.controller.AuthController;
import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.security.OidcLoginSuccessHandler;
import com.mintpop.shop.security.PostLoginRedirectCookie;
import com.mintpop.shop.service.SessionTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全链切片测试：验证 401/permitAll 边界与会话 Cookie 认证的接线。
 * 只加载 AuthController + SecurityConfig，受保护业务接口在本切片中不存在（404 即代表已过鉴权）。
 */
// 排除全局异常 advice：否则它会把「无对应 handler」的 NoResourceFoundException 兜成 HTTP 200
// 业务错误体，破坏本切片「404 即已过鉴权」的判定基准
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = GlobalExceptionHandler.class))
@Import(SecurityConfig.class)
class SecurityChainTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SessionTokenService sessionTokenService;
    @MockitoBean
    private OidcLoginSuccessHandler oidcLoginSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean
    private AuthProperties authProperties;
    // WebConfig 注册的管理端拦截器依赖 mapper，切片无 SqlSessionFactory，mock 兜底
    @MockitoBean
    private ShopUserMapper shopUserMapper;
    @MockitoBean
    private PostLoginRedirectCookie postLoginRedirectCookie;

    @Test
    @DisplayName("未登录访问 /api/me 返回 401")
    void meWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登录访问 /api/orders 返回 401")
    void ordersWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("公开路径不拦截：/api/groups 未登录不是 401")
    void groupsIsPublic() throws Exception {
        // 本切片未加载 GroupController，404 即证明请求穿过了安全链而非被 401 拦下
        mockMvc.perform(get("/api/groups")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("带有效会话 Cookie 可通过鉴权（切片中无 MeController，404 即已过鉴权）")
    void validSessionCookiePassesAuth() throws Exception {
        when(sessionTokenService.parse("token-1")).thenReturn(Optional.of(42L));
        mockMvc.perform(get("/api/me").cookie(new Cookie("mp_session", "token-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未登记的 /api 新接口默认需登录（白名单姿态）")
    void unregisteredApiPathRequiresAuthByDefault() throws Exception {
        mockMvc.perform(get("/api/anything-new")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Stripe webhook 路径公开：未登录不是 401（自身安全靠验签）")
    void stripeWebhookIsPublic() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/payment/webhook/stripe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("授权请求带 prompt=login：点登录必须重新走账号中心登录页")
    void authorizationRequestForcesLoginPrompt() throws Exception {
        when(clientRegistrationRepository.findByRegistrationId("logto")).thenReturn(
                ClientRegistration.withRegistrationId("logto")
                        .clientId("client-1")
                        .clientSecret("secret-1")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/auth/callback")
                        .authorizationUri("https://auth.example.com/oidc/auth")
                        .tokenUri("https://auth.example.com/oidc/token")
                        .scope("openid")
                        .build());

        mockMvc.perform(get("/oauth2/authorization/logto"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("prompt=login")));
    }

    @Test
    @DisplayName("OIDC 握手失败（如 state 不符/用户取消）：清掉回跳路径 Cookie，避免污染下一次登录")
    void oidcLoginFailureClearsPostLoginRedirectCookie() throws Exception {
        when(authProperties.getFrontendBaseUrl()).thenReturn("/");

        // 无 mp_oidc_flow 中间态 Cookie 时携带 error 参数回调 /auth/callback：
        // OAuth2LoginAuthenticationFilter 会因找不到握手中间态而判定失败，走到 failureHandler，
        // 与「state 不符/用户取消」殊途同归，足以覆盖 failureHandler 这条清 Cookie 的分支
        mockMvc.perform(get("/auth/callback").param("error", "access_denied").param("state", "s1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?login_error=1"));

        verify(postLoginRedirectCookie).expire(any(), any());
    }
}
