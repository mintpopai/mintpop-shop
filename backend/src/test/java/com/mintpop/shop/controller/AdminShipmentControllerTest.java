package com.mintpop.shop.controller;

import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.AdminShipmentItemResponse;
import com.mintpop.shop.response.AdminShipmentResponse;
import com.mintpop.shop.security.CurrentUserIdArgumentResolver;
import com.mintpop.shop.service.AdminShipmentService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminShipmentControllerTest {

    private MockMvc mockMvc;
    private AdminShipmentService adminShipmentService;

    @BeforeEach
    void setUp() {
        adminShipmentService = mock(AdminShipmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminShipmentController(adminShipmentService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(TestMessages.create()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        99L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("发货成功：带上当前管理员ID，回传邮件状态")
    void ships() throws Exception {
        when(adminShipmentService.ship(eq("order-1"), eq("兑换码：ABC"), isNull(), eq(99L)))
                .thenReturn(new AdminShipmentResponse(Instant.parse("2026-07-31T12:00:00Z"), "SENT", null));

        mockMvc.perform(post("/api/admin/orders/order-1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"兑换码：ABC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.emailStatus").value("SENT"));

        verify(adminShipmentService).ship("order-1", "兑换码：ABC", null, 99L);
    }

    @Test
    @DisplayName("发货内容为空：参数校验失败")
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/admin/orders/order-1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));
    }

    @Test
    @DisplayName("发货历史按服务层顺序下发")
    void listsHistory() throws Exception {
        when(adminShipmentService.listShipments("order-1")).thenReturn(List.of(
                new AdminShipmentItemResponse(2L, "第二次", "发错了", "admin@mintpop.ai",
                        "buyer@example.com", "FAILED", "connect timed out",
                        Instant.parse("2026-07-31T13:00:00Z"))));

        mockMvc.perform(get("/api/admin/orders/order-1/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].content").value("第二次"))
                .andExpect(jsonPath("$.data[0].emailStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[0].operatorEmail").value("admin@mintpop.ai"));
    }
}
