package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 订单支付核实结果。前端成功口径：status 为 PAID 或 COMPLETED。
 */
@Data
@AllArgsConstructor
public class VerifyOrderResponse {

    /** 对外订单号 */
    private String orderNo;
    /** 订单状态（SCREAMING_SNAKE_CASE） */
    private String status;
}
