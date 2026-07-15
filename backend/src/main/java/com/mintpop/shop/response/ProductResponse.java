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
    /** 价格，单位美分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;
    /** 角标（按语言回退），空=不显示 */
    private String badge;
    /** 卡片主题色枚举：MINT/VIOLET/SKY/AMBER/ROSE */
    private String accent;

    public static ProductResponse of(Product product, boolean english) {
        return new ProductResponse(product.getId(),
                I18nUtil.pick(english, product.getNameEn(), product.getNameZh()),
                I18nUtil.pick(english, product.getDescriptionEn(), product.getDescriptionZh()),
                product.getPriceCents(), product.getImageUrl(),
                I18nUtil.pick(english, product.getBadgeEn(), product.getBadgeZh()),
                product.getAccent());
    }
}
