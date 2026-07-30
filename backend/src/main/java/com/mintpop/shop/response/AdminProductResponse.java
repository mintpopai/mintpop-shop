package com.mintpop.shop.response;

import com.mintpop.shop.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理端商品响应体：下发双语原始字段供编辑，不做语言回退。
 */
@Data
@AllArgsConstructor
public class AdminProductResponse {

    /** 商品ID */
    private Long id;
    /** 所属分组ID */
    private Long groupId;
    /** 商品名（中文） */
    private String nameZh;
    /** 商品名（英文） */
    private String nameEn;
    /** 商品描述（中文） */
    private String descriptionZh;
    /** 商品描述（英文） */
    private String descriptionEn;
    /** 角标（中文） */
    private String badgeZh;
    /** 角标（英文） */
    private String badgeEn;
    /** 卡片主题色枚举 */
    private String accent;
    /** 价格，单位美分 */
    private Long priceCents;
    /** 商品图URL */
    private String imageUrl;
    /** 是否上架 */
    private Boolean onSale;

    public static AdminProductResponse of(Product p) {
        return new AdminProductResponse(p.getId(), p.getGroupId(), p.getNameZh(), p.getNameEn(),
                p.getDescriptionZh(), p.getDescriptionEn(), p.getBadgeZh(), p.getBadgeEn(),
                p.getAccent(), p.getPriceCents(), p.getImageUrl(), p.getOnSale());
    }
}
