package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 支付意图响应：前端据 clientSecret 走 Stripe.js 确认，其余字段供支付页摘要展示。
 */
@Data
@AllArgsConstructor
public class PaymentIntentResponse {

    /** 对外订单号（即写入 PaymentIntent Metadata.orderId 的我方单号） */
    private String orderNo;
    /** Stripe client_secret */
    private String clientSecret;
    /** 应付金额（最小货币单位，CNY 即分） */
    private Long amountCents;
    /** 币种（3 位 ISO，如 CNY） */
    private String currency;
    /** 商品名（按请求语言） */
    private String productName;
    /** 购买数量 */
    private Integer quantity;
}
