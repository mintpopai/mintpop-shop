package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 收银台信息：可用支付方式与前端初始化 Stripe.js 所需的 publishable key。
 */
@Data
@AllArgsConstructor
public class CheckoutInfoResponse {

    /** 可用支付方式（当前仅 stripe；未配置时为空列表） */
    private List<String> methods;
    /** Stripe publishable key（非敏感；未配置时为 null） */
    private String stripePublishableKey;
}
