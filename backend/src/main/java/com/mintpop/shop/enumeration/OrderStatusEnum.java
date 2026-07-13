package com.mintpop.shop.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态：成员名与持久化字符串取值逐字一致（SCREAMING_SNAKE_CASE）。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING_PAYMENT("PENDING_PAYMENT", "待支付");

    /** 持久化到数据库的取值 */
    @EnumValue
    private final String value;
    /** 中文描述 */
    private final String label;
}
