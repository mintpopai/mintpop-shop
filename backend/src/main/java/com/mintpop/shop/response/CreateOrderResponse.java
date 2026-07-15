package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 下单成功响应体。
 */
@Data
@AllArgsConstructor
public class CreateOrderResponse {

    /** 对外订单号 */
    private String orderNo;
    /** 订单金额，单位美分 */
    private Long amountCents;
}
