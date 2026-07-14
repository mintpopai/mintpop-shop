package com.mintpop.shop.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下单请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {

    /** 商品ID */
    @NotNull(message = "{biz.validation.product-id-required}")
    private Long productId;

    /** 购买数量（1~99） */
    @NotNull(message = "{biz.validation.quantity-required}")
    @Min(value = 1, message = "{biz.validation.quantity-min}")
    @Max(value = 99, message = "{biz.validation.quantity-max}")
    private Integer quantity;
}
