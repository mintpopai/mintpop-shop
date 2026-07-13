package com.mintpop.shop.controller;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.CreateOrderResponse;
import com.mintpop.shop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("下单成功返回 code 0 与订单号")
    void createOrderSuccess() throws Exception {
        when(orderService.createOrder(ArgumentMatchers.any()))
                .thenReturn(new CreateOrderResponse("MP20260713120000123456", 5900L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("MP20260713120000123456"));
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
        when(orderService.createOrder(ArgumentMatchers.any()))
                .thenThrow(new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210001))
                .andExpect(jsonPath("$.msg").value("商品不存在或已下架"));
    }
}
