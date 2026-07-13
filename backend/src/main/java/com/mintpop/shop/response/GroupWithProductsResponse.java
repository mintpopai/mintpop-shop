package com.mintpop.shop.response;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分组（含分组下上架商品）响应体。
 */
@Data
@AllArgsConstructor
public class GroupWithProductsResponse {

    /** 分组ID */
    private Long id;
    /** 分组名 */
    private String name;
    /** 分组下上架商品列表 */
    private List<ProductResponse> products;

    public static GroupWithProductsResponse of(ProductGroup group, List<Product> products) {
        return new GroupWithProductsResponse(group.getId(), group.getName(),
                products.stream().map(ProductResponse::of).toList());
    }
}
