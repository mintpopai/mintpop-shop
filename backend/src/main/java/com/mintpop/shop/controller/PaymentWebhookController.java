package com.mintpop.shop.controller;

import com.mintpop.shop.client.StripeGateway;
import com.mintpop.shop.client.StripeWebhookEvent;
import com.mintpop.shop.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Stripe webhook 回调（品牌规范固定路径）。机器对机器接口，不走 ApiResponse：
 * 验签失败/缺签名/超长回 400，处理异常回 500（让 Stripe 重试），其余一律 200 空响应。
 * 异常必须在本控制器内消化——若漏给全局异常处理器会被包装成 HTTP 200，Stripe 将视为已送达。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {

    /** 原始载荷长度上限（1MB） */
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private final StripeGateway stripeGateway;
    private final PaymentService paymentService;

    @PostMapping("/api/v1/payment/webhook/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody(required = false) byte[] payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (payload == null || payload.length == 0
                || payload.length > MAX_PAYLOAD_BYTES || signature == null) {
            return ResponseEntity.badRequest().build();
        }
        StripeWebhookEvent event;
        try {
            event = stripeGateway.parseWebhookEvent(
                    new String(payload, StandardCharsets.UTF_8), signature);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook 验签失败", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // 密钥未配置等意外异常：回 500 让 Stripe 重试，绝不能逃逸给全局 advice 变 200
            log.error("Stripe webhook 解析失败", e);
            return ResponseEntity.internalServerError().build();
        }
        try {
            paymentService.handleWebhook(event);
        } catch (Exception e) {
            log.error("Stripe webhook 处理失败 type={}", event.type(), e);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }
}
