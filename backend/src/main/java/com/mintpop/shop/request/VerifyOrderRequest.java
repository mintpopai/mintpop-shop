package com.mintpop.shop.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 核实订单支付状态请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyOrderRequest {

    /** 对外订单号 */
    @NotBlank(message = "{biz.validation.order-no-required}")
    private String orderNo;
}
