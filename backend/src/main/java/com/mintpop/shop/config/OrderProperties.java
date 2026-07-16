package com.mintpop.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 订单业务配置（app.order.*）。
 */
@Data
@ConfigurationProperties(prefix = "app.order")
public class OrderProperties {

    /** 待支付订单的支付时限（分钟）：超过后懒惰过期为 EXPIRED，不再受理支付 */
    private long expireMinutes = 30;
}
