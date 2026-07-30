package com.mintpop.shop.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码：6 位分段编码，前两位为模块号（11=通用，21=商品，31=用户/账号，41=支付），后四位为段内序号。
 * 文案不落在枚举里，只存消息 key，由 MessageSource 按请求语言解析（见 messages*.properties）。
 */
@Getter
@AllArgsConstructor
public enum BizCodeEnum {

    SYSTEM_ERROR(110001, "biz.system-error"),
    PARAM_INVALID(110002, "biz.param-invalid"),
    PERMISSION_DENIED(110003, "biz.permission-denied"),

    PRODUCT_NOT_ON_SALE(210001, "biz.product-not-on-sale"),
    PRODUCT_NOT_FOUND(210002, "biz.product-not-found"),
    GROUP_NOT_FOUND(210003, "biz.group-not-found"),
    GROUP_NOT_EMPTY(210004, "biz.group-not-empty"),

    USER_NOT_FOUND(310001, "biz.user-not-found"),

    ORDER_NOT_FOUND(410001, "biz.order-not-found"),
    ORDER_NOT_PAYABLE(410002, "biz.order-not-payable"),
    PAYMENT_NOT_CONFIGURED(410003, "biz.payment-not-configured"),
    PAYMENT_GATEWAY_ERROR(410004, "biz.payment-gateway-error"),
    ORDER_NOT_CANCELLABLE(410005, "biz.order-not-cancellable");

    private final int code;
    private final String messageKey;
}
