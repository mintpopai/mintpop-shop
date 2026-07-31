package com.mintpop.shop.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发货邮件发送结果：成员名与持久化字符串取值逐字一致（SCREAMING_SNAKE_CASE）。
 */
@Getter
@AllArgsConstructor
public enum ShipmentEmailStatusEnum {

    SENT("SENT"),
    FAILED("FAILED");

    /** 持久化到数据库的取值 */
    @EnumValue
    private final String value;
}
