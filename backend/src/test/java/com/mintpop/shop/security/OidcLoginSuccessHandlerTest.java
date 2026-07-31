package com.mintpop.shop.security;

import com.mintpop.shop.config.AuthProperties;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.service.SessionTokenService;
import com.mintpop.shop.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcLoginSuccessHandlerTest {

    @Mock
    private UserService userService;
    @Mock
    private SessionTokenService sessionTokenService;
    @Spy
    private AuthProperties authProperties = new AuthProperties();
    @InjectMocks
    private OidcLoginSuccessHandler handler;

    private Authentication oidcAuthentication() {
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "sub-1", "email", "a@b.com", "name", "小明", "picture", "https://img/x.png"));
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        return authentication;
    }

    @Test
    @DisplayName("登录成功：同步用户、写 HttpOnly 会话 Cookie、302 回前端")
    void loginSuccessIssuesSessionCookie() throws Exception {
        ShopUser user = new ShopUser();
        user.setId(7L);
        when(userService.syncOnLogin("sub-1", "a@b.com", "小明", "https://img/x.png", "en-US")).thenReturn(user);
        when(sessionTokenService.issue(7L)).thenReturn("session-jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, oidcAuthentication());

        verify(userService).syncOnLogin("sub-1", "a@b.com", "小明", "https://img/x.png", "en-US");
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(AuthProperties.SESSION_COOKIE_NAME + "=session-jwt")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/");
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }
}
