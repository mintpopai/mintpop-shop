package com.mintpop.shop.controller;

import com.mintpop.shop.client.StripeGateway;
import com.mintpop.shop.client.StripeWebhookEvent;
import com.mintpop.shop.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookControllerTest {

    private static final String PATH = "/api/v1/payment/webhook/stripe";

    private MockMvc mockMvc;
    private StripeGateway stripeGateway;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        stripeGateway = mock(StripeGateway.class);
        paymentService = mock(PaymentService.class);
        // 故意不挂 GlobalExceptionHandler：webhook 的异常必须在控制器内消化成原生状态码
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentWebhookController(stripeGateway, paymentService))
                .build();
    }

    @Test
    @DisplayName("验签通过：处理事件并回 200")
    void validEventReturns200() throws Exception {
        StripeWebhookEvent event = new StripeWebhookEvent(
                "payment_intent.succeeded", "pi_1", "MP1", 11800L, "cny");
        when(stripeGateway.parseWebhookEvent(anyString(), eq("sig-header"))).thenReturn(event);

        mockMvc.perform(post(PATH)
                        .header("Stripe-Signature", "sig-header")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhook(event);
    }

    @Test
    @DisplayName("验签失败回 400")
    void badSignatureReturns400() throws Exception {
        when(stripeGateway.parseWebhookEvent(anyString(), anyString()))
                .thenThrow(new SignatureVerificationException("bad", "sig"));

        mockMvc.perform(post(PATH)
                        .header("Stripe-Signature", "bad-sig")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("缺 Stripe-Signature 头回 400")
    void missingSignatureReturns400() throws Exception {
        mockMvc.perform(post(PATH).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("超过 1MB 的载荷回 400")
    void oversizedPayloadReturns400() throws Exception {
        byte[] big = new byte[1024 * 1024 + 1];
        mockMvc.perform(post(PATH)
                        .header("Stripe-Signature", "sig")
                        .content(big))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("业务处理抛异常回 500（让 Stripe 重试，绝不能被包装成 200）")
    void handlerFailureReturns500() throws Exception {
        StripeWebhookEvent event = new StripeWebhookEvent(
                "payment_intent.succeeded", "pi_1", "MP1", 11800L, "cny");
        when(stripeGateway.parseWebhookEvent(anyString(), anyString())).thenReturn(event);
        doThrow(new RuntimeException("db down"))
                .when(paymentService).handleWebhook(any());

        mockMvc.perform(post(PATH)
                        .header("Stripe-Signature", "sig")
                        .content("{}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("解析阶段非验签异常回 500（不能逃逸成 200）")
    void parseFailureReturns500() throws Exception {
        when(stripeGateway.parseWebhookEvent(anyString(), anyString()))
                .thenThrow(new RuntimeException("webhook secret 未配置"));

        mockMvc.perform(post(PATH)
                        .header("Stripe-Signature", "sig")
                        .content("{}"))
                .andExpect(status().isInternalServerError());
    }
}
