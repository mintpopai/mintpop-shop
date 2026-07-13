package com.mintpop.shop.controller;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.CreateOrderResponse;
import com.mintpop.shop.response.OrderItemResponse;
import com.mintpop.shop.security.CurrentUserIdArgumentResolver;
import com.mintpop.shop.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
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
    @DisplayName("下单成功返回 code 0 与订单号，并带上当前用户")
    void createOrderSuccess() throws Exception {
        when(orderService.createOrder(eq(42L), ArgumentMatchers.any()))
                .thenReturn(new CreateOrderResponse("MP20260713120000123456", 5900L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("MP20260713120000123456"));

        verify(orderService).createOrder(eq(42L), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("数量为 0 触发参数校验，返回 110002")
    void invalidQuantityReturnsParamError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));
    }

    @Test
    @DisplayName("商品不存在返回 210001（HTTP 仍为 200）")
    void productNotOnSaleReturnsBizCode() throws Exception {
        when(orderService.createOrder(eq(42L), ArgumentMatchers.any()))
                .thenThrow(new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210001))
                .andExpect(jsonPath("$.msg").value("商品不存在或已下架"));
    }

    @Test
    @DisplayName("我的订单列表返回当前用户的订单")
    void listMyOrdersReturnsOwnOrders() throws Exception {
        when(orderService.listMyOrders(42L)).thenReturn(List.of(new OrderItemResponse(
                "MP20260713120000123456", "薄荷精灵盲盒", 2, 11800L,
                "PENDING_PAYMENT", "待支付", LocalDateTime.of(2026, 7, 13, 12, 0))));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].productName").value("薄荷精灵盲盒"))
                .andExpect(jsonPath("$.data[0].statusLabel").value("待支付"));
    }
}
