package com.mintpop.shop.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码：6 位分段编码，前两位为模块号（11=通用，21=商品，31=用户/账号），后四位为段内序号。
 */
@Getter
@AllArgsConstructor
public enum BizCodeEnum {

    SYSTEM_ERROR(110001, "系统繁忙，请稍后重试"),
    PARAM_INVALID(110002, "参数校验失败"),

    PRODUCT_NOT_ON_SALE(210001, "商品不存在或已下架"),

    USER_NOT_FOUND(310001, "用户不存在，请重新登录");

    private final int code;
    private final String message;
}
