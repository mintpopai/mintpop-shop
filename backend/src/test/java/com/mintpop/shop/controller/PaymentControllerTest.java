package com.mintpop.shop.controller;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.CheckoutInfoResponse;
import com.mintpop.shop.response.PaymentIntentResponse;
import com.mintpop.shop.response.VerifyOrderResponse;
import com.mintpop.shop.security.CurrentUserIdArgumentResolver;
import com.mintpop.shop.service.PaymentService;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest {

    private MockMvc mockMvc;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
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
    @DisplayName("收银台信息返回通道与 publishable key")
    void checkoutInfo() throws Exception {
        when(paymentService.checkoutInfo())
                .thenReturn(new CheckoutInfoResponse(List.of("stripe"), "test-publishable"));

        mockMvc.perform(get("/api/payment/checkout-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.methods[0]").value("stripe"))
                .andExpect(jsonPath("$.data.stripePublishableKey").value("test-publishable"));
    }

    @Test
    @DisplayName("发起支付意图带上当前用户与路径单号")
    void createIntent() throws Exception {
        when(paymentService.getOrCreateIntent(42L, "MP1"))
                .thenReturn(new PaymentIntentResponse("MP1", "pi_secret", 11800L, "USD",
                        "薄荷精灵盲盒", 2));

        mockMvc.perform(post("/api/payment/orders/MP1/intent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.clientSecret").value("pi_secret"));

        verify(paymentService).getOrCreateIntent(42L, "MP1");
    }

    @Test
    @DisplayName("不可支付状态返回 410002（HTTP 仍 200）")
    void notPayableReturnsBizCode() throws Exception {
        when(paymentService.getOrCreateIntent(42L, "MP1"))
                .thenThrow(new BizException(BizCodeEnum.ORDER_NOT_PAYABLE));

        mockMvc.perform(post("/api/payment/orders/MP1/intent")
                        .header("Accept-Language", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410002))
                .andExpect(jsonPath("$.msg").value("订单当前状态不可支付"));
    }

    @Test
    @DisplayName("核实订单状态")
    void verifyOrder() throws Exception {
        when(paymentService.verify(42L, "MP1"))
                .thenReturn(new VerifyOrderResponse("MP1", "PAID"));

        mockMvc.perform(post("/api/payment/orders/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"MP1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("核实缺单号触发参数校验 110002")
    void verifyWithoutOrderNo() throws Exception {
        mockMvc.perform(post("/api/payment/orders/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));
    }

    @Test
    @DisplayName("取消订单")
    void cancelOrder() throws Exception {
        mockMvc.perform(post("/api/payment/orders/MP1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(paymentService).cancel(42L, "MP1");
    }
}
