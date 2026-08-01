package com.mintpop.shop.controller;

import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.MeResponse;
import com.mintpop.shop.security.CurrentUserIdArgumentResolver;
import com.mintpop.shop.service.UserService;
import com.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerTest {

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MeController(userService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(TestMessages.create()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("返回当前登录用户档案")
    void returnsCurrentUser() throws Exception {
        when(userService.getMe(42L)).thenReturn(
                new MeResponse(42L, "a@b.com", "小明", "https://img/x.png", false, "zh-CN"));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.email").value("a@b.com"))
                .andExpect(jsonPath("$.data.nickname").value("小明"));
    }

    @Test
    @DisplayName("保存语言偏好")
    void updatesLocale() throws Exception {
        mockMvc.perform(put("/api/me/locale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"en-US\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).updateLocale(42L, "en-US");
    }

    @Test
    @DisplayName("保存个人档案：昵称与语言一次提交")
    void updatesProfile() throws Exception {
        mockMvc.perform(put("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小明\",\"locale\":\"en-US\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).updateProfile(42L, "小明", "en-US");
    }

    @Test
    @DisplayName("保存个人档案：昵称为空被请求校验拦下，不进 service")
    void rejectsBlankNickname() throws Exception {
        mockMvc.perform(put("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\",\"locale\":\"zh-CN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));

        verify(userService, never()).updateProfile(any(), any(), any());
    }

    @Test
    @DisplayName("保存个人档案：昵称超过 30 字符被请求校验拦下，不进 service")
    void rejectsTooLongNickname() throws Exception {
        mockMvc.perform(put("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + "名".repeat(31) + "\",\"locale\":\"zh-CN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));

        verify(userService, never()).updateProfile(any(), any(), any());
    }
}
