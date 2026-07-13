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
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 购买数量（1~99） */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 99, message = "购买数量最多为 99")
    private Integer quantity;
}
