package com.mintpop.shop.client;

import com.mintpop.shop.config.PaymentProperties;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Stripe SDK 唯一封装点：创建/检索 PaymentIntent、webhook 验签。
 * 业务分支不在这层，出错记日志后转业务异常，便于服务层用 fake 替换测试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeGateway {

    private final PaymentProperties properties;
    /** 懒初始化的 Stripe 客户端（配置未填时保持 null，用到才报未配置） */
    private volatile StripeClient client;

    /**
     * 创建 PaymentIntent（品牌硬约定：幂等键 pi-<单号>、Metadata.orderId、微信必带 client=web）。
     * 金额直接取订单 amount_cents——数据库存的就是最小货币单位（CNY 分），不做 *100 之类换算。
     */
    public PaymentIntent createPaymentIntent(String orderNo, long amountMinorUnit,
                                             String subject, List<String> paymentMethodTypes) {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountMinorUnit)
                .setCurrency(properties.getCurrency().toLowerCase(Locale.ROOT))
                .setDescription(subject)
                .putMetadata("orderId", orderNo);
        paymentMethodTypes.forEach(builder::addPaymentMethodType);
        if (paymentMethodTypes.contains("wechat_pay")) {
            builder.setPaymentMethodOptions(
                    PaymentIntentCreateParams.PaymentMethodOptions.builder()
                            .setWechatPay(PaymentIntentCreateParams.PaymentMethodOptions.WechatPay.builder()
                                    .setClient(PaymentIntentCreateParams.PaymentMethodOptions
                                            .WechatPay.Client.WEB)
                                    .build())
                            .build());
        }
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("pi-" + orderNo)
                .build();
        try {
            return client().v1().paymentIntents().create(builder.build(), options);
        } catch (StripeException e) {
            log.error("创建 PaymentIntent 失败 orderNo={}", orderNo, e);
            throw new BizException(BizCodeEnum.PAYMENT_GATEWAY_ERROR);
        }
    }

    /** 按 ID 检索 PaymentIntent（旧单续付取回 client_secret / 主动核实状态用） */
    public PaymentIntent retrievePaymentIntent(String intentId) {
        try {
            return client().v1().paymentIntents().retrieve(intentId);
        } catch (StripeException e) {
            log.error("检索 PaymentIntent 失败 intentId={}", intentId, e);
            throw new BizException(BizCodeEnum.PAYMENT_GATEWAY_ERROR);
        }
    }

    /**
     * webhook 验签并解出业务字段。验签失败抛 SignatureVerificationException（调用方回 400）；
     * 事件对象反序列化失败（API 版本漂移）不视为错误，返回只带类型的事件由服务层忽略。
     */
    public StripeWebhookEvent parseWebhookEvent(String payload, String signatureHeader)
            throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, signatureHeader, requiredWebhookSecret());
        PaymentIntent intent = null;
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        try {
            StripeObject object = deserializer.getObject().isPresent()
                    ? deserializer.getObject().get()
                    : deserializer.deserializeUnsafe();
            if (object instanceof PaymentIntent pi) {
                intent = pi;
            }
        } catch (Exception e) {
            log.warn("webhook 事件对象反序列化失败 eventType={}", event.getType(), e);
        }
        if (intent == null) {
            return new StripeWebhookEvent(event.getType(), null, null, null, null);
        }
        return new StripeWebhookEvent(event.getType(), intent.getId(),
                intent.getMetadata() == null ? null : intent.getMetadata().get("orderId"),
                intent.getAmount(), intent.getCurrency());
    }

    private StripeClient client() {
        if (!properties.isConfigured()) {
            throw new BizException(BizCodeEnum.PAYMENT_NOT_CONFIGURED);
        }
        StripeClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = new StripeClient(properties.getSecretKey());
                }
                c = client;
            }
        }
        return c;
    }

    private String requiredWebhookSecret() {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BizException(BizCodeEnum.PAYMENT_NOT_CONFIGURED);
        }
        return secret;
    }
}
