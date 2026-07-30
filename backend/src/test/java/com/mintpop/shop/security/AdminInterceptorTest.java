package com.mintpop.shop.security;

import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ShopUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInterceptorTest {

    @Mock
    private ShopUserMapper shopUserMapper;
    @Mock
    private AdminChecker adminChecker;
    @InjectMocks
    private AdminInterceptor interceptor;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private ShopUser user(long id, String email) {
        ShopUser u = new ShopUser();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    @Test
    @DisplayName("管理员放行")
    void adminPasses() {
        loginAs(7L);
        when(shopUserMapper.selectById(7L)).thenReturn(user(7L, "boss@mintpop.ai"));
        when(adminChecker.isAdmin("boss@mintpop.ai")).thenReturn(true);

        boolean pass = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertThat(pass).isTrue();
    }

    @Test
    @DisplayName("非管理员抛 110003 权限不足")
    void nonAdminRejected() {
        loginAs(8L);
        when(shopUserMapper.selectById(8L)).thenReturn(user(8L, "user@x.com"));
        when(adminChecker.isAdmin("user@x.com")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("会话用户已不存在同样拒绝")
    void missingUserRejected() {
        loginAs(9L);
        when(shopUserMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("未取到登录用户属安全配置错误，快速失败")
    void missingAuthenticationFailsFast() {
        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(IllegalStateException.class);
    }
}
