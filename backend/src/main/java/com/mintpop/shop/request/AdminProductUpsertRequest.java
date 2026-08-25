package com.mintpop.shop.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端商品新增/编辑请求体（两接口共用同一形状）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminProductUpsertRequest {

    /** 所属分组ID */
    @NotNull(message = "{biz.validation.group-id-required}")
    private Long groupId;

    /** 商品名（中文，必填兜底语言） */
    @NotBlank(message = "{biz.validation.name-zh-required}")
    private String nameZh;

    /** 商品名（英文），空回退中文 */
    private String nameEn;

    /** 商品描述（中文） */
    private String descriptionZh;

    /** 商品描述（英文），空回退中文 */
    private String descriptionEn;

    /** 商品详情富文本HTML（中文），后端入库前净化；上限防撑爆 MEDIUMTEXT */
    @Size(max = 50000, message = "{biz.validation.detail-too-long}")
    private String detailZh;

    /** 商品详情富文本HTML（英文），空回退中文 */
    @Size(max = 50000, message = "{biz.validation.detail-too-long}")
    private String detailEn;

    /** 角标（中文），空=不显示 */
    private String badgeZh;

    /** 角标（英文），空回退中文 */
    private String badgeEn;

    /** 卡片主题色枚举 */
    @NotNull(message = "{biz.validation.accent-invalid}")
    @Pattern(regexp = "MINT|VIOLET|SKY|AMBER|ROSE", message = "{biz.validation.accent-invalid}")
    private String accent;

    /** 价格，单位美分（至少 1 美分） */
    @NotNull(message = "{biz.validation.price-required}")
    @Min(value = 1, message = "{biz.validation.price-min}")
    private Long priceCents;

    /** 商品图URL，可空 */
    private String imageUrl;

    /** 是否上架 */
    @NotNull(message = "{biz.validation.on-sale-required}")
    private Boolean onSale;
}
