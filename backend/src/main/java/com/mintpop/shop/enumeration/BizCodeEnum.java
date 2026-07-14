package com.mintpop.shop.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码：6 位分段编码，前两位为模块号（11=通用，21=商品，31=用户/账号），后四位为段内序号。
 * 文案不落在枚举里，只存消息 key，由 MessageSource 按请求语言解析（见 messages*.properties）。
 */
@Getter
@AllArgsConstructor
public enum BizCodeEnum {

    SYSTEM_ERROR(110001, "biz.system-error"),
    PARAM_INVALID(110002, "biz.param-invalid"),

    PRODUCT_NOT_ON_SALE(210001, "biz.product-not-on-sale"),

    USER_NOT_FOUND(310001, "biz.user-not-found");

    private final int code;
    private final String messageKey;
}
