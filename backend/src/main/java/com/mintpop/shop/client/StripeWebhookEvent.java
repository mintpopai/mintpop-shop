package com.mintpop.shop.client;

/**
 * Stripe webhook 事件值对象：网关层验签并解出业务关心的最小字段，供服务层处理。
 * 事件不携带 PaymentIntent（如非支付类事件）时，intentId 及之后的字段为空。
 */
public record StripeWebhookEvent(
        /* 事件类型（如 payment_intent.succeeded） */ String type,
        /* PaymentIntent ID */ String intentId,
        /* 我方订单号（Metadata["orderId"]） */ String orderNo,
        /* 业务线标记（Metadata["product"]；账户多业务共用时据此认领事件） */ String product,
        /* 金额（最小货币单位） */ Long amountMinorUnit,
        /* 币种（小写 ISO） */ String currency) {
}
