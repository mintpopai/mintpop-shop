package com.mintpop.shop.response;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.util.I18nUtil;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 商品响应体。
 */
@Data
@AllArgsConstructor
public class ProductResponse {

    /** 商品ID */
    private Long id;
    /** 商品名 */
    private String name;
    /** 商品描述 */
    private String description;
    /** 价格，单位分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;

    public static ProductResponse of(Product product, boolean english) {
        return new ProductResponse(product.getId(),
                I18nUtil.pick(english, product.getNameEn(), product.getNameZh()),
                I18nUtil.pick(english, product.getDescriptionEn(), product.getDescriptionZh()),
                product.getPriceCents(), product.getImageUrl());
    }
}
