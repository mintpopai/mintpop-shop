package com.mintpop.shop.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端商品上/下架请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminProductOnSaleRequest {

    /** 目标状态：true=上架 false=下架 */
    @NotNull(message = "{biz.validation.on-sale-required}")
    private Boolean onSale;
}
