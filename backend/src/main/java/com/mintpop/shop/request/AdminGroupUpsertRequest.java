package com.mintpop.shop.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端分组新增/编辑请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminGroupUpsertRequest {

    /** 分组名（中文，必填兜底语言） */
    @NotBlank(message = "{biz.validation.name-zh-required}")
    private String nameZh;

    /** 分组名（英文），空回退中文 */
    private String nameEn;

    /** 排序号，小的在前 */
    @NotNull(message = "{biz.validation.sort-order-required}")
    private Integer sortOrder;
}
