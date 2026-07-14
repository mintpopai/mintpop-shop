package com.mintpop.shop.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态：成员名与持久化字符串取值逐字一致（SCREAMING_SNAKE_CASE）。
 * 展示文案不落在枚举里，只存消息 key，由 MessageSource 按请求语言解析。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING("PENDING", "order.status.pending"),
    PAID("PAID", "order.status.paid"),
    COMPLETED("COMPLETED", "order.status.completed"),
    CANCELLED("CANCELLED", "order.status.cancelled"),
    EXPIRED("EXPIRED", "order.status.expired"),
    FAILED("FAILED", "order.status.failed");

    /** 持久化到数据库的取值 */
    @EnumValue
    private final String value;
    /** 状态展示文案的消息 key */
    private final String labelKey;
}
