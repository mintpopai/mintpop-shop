package com.mintpop.shop.response;

import com.mintpop.shop.entity.ProductGroup;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理端分组响应体：下发双语原始字段供编辑。
 */
@Data
@AllArgsConstructor
public class AdminGroupResponse {

    /** 分组ID */
    private Long id;
    /** 分组名（中文） */
    private String nameZh;
    /** 分组名（英文） */
    private String nameEn;
    /** 排序号，小的在前 */
    private Integer sortOrder;
    /** 组内商品数（含下架；非 0 不可删） */
    private Long productCount;

    public static AdminGroupResponse of(ProductGroup g, long productCount) {
        return new AdminGroupResponse(g.getId(), g.getNameZh(), g.getNameEn(), g.getSortOrder(), productCount);
    }
}
