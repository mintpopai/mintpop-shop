package com.mintpop.shop.controller;

import com.mintpop.shop.request.CreateOrderRequest;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.CreateOrderResponse;
import com.mintpop.shop.response.OrderDetailResponse;
import com.mintpop.shop.response.OrderItemResponse;
import com.mintpop.shop.security.CurrentUserId;
import com.mintpop.shop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口（均需登录，未登录由安全链返回 401）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建待支付订单（绑定当前登录用户） */
    @PostMapping("/orders")
    public ApiResponse<CreateOrderResponse> createOrder(@CurrentUserId Long userId,
                                                        @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(userId, request));
    }

    /** 我的订单列表（按下单时间倒序） */
    @GetMapping("/orders")
    public ApiResponse<List<OrderItemResponse>> listMyOrders(@CurrentUserId Long userId) {
        return ApiResponse.success(orderService.listMyOrders(userId));
    }

    /** 我的订单详情（含最新发货信息） */
    @GetMapping("/orders/{orderNo}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@CurrentUserId Long userId,
                                                           @PathVariable String orderNo) {
        return ApiResponse.success(orderService.getMyOrderDetail(userId, orderNo));
    }
}
