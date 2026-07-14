package com.mintpop.shop.controller;

import com.mintpop.shop.request.VerifyOrderRequest;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.CheckoutInfoResponse;
import com.mintpop.shop.response.PaymentIntentResponse;
import com.mintpop.shop.response.VerifyOrderResponse;
import com.mintpop.shop.security.CurrentUserId;
import com.mintpop.shop.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口（均需登录；订单归属在服务层校验）。
 * 子方式（微信/支付宝/银行卡）只在前端展示层，本控制器不感知。
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 收银台信息：可用支付方式 + Stripe publishable key */
    @GetMapping("/checkout-info")
    public ApiResponse<CheckoutInfoResponse> checkoutInfo() {
        return ApiResponse.success(paymentService.checkoutInfo());
    }

    /** 懒创建/复用支付意图（支付页加载时调用，新旧单同一条路） */
    @PostMapping("/orders/{orderNo}/intent")
    public ApiResponse<PaymentIntentResponse> createIntent(@CurrentUserId Long userId,
                                                           @PathVariable String orderNo) {
        return ApiResponse.success(paymentService.getOrCreateIntent(userId, orderNo));
    }

    /** 主动向网关核实并推进状态（前端 2s 轮询用） */
    @PostMapping("/orders/verify")
    public ApiResponse<VerifyOrderResponse> verify(@CurrentUserId Long userId,
                                                   @Valid @RequestBody VerifyOrderRequest request) {
        return ApiResponse.success(paymentService.verify(userId, request.getOrderNo()));
    }

    /** 取消订单（仅待支付/支付失败可取消） */
    @PostMapping("/orders/{orderNo}/cancel")
    public ApiResponse<Void> cancel(@CurrentUserId Long userId, @PathVariable String orderNo) {
        paymentService.cancel(userId, orderNo);
        return ApiResponse.success();
    }
}
