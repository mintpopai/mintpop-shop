package ai.mintpop.shop.response;

import ai.mintpop.shop.entity.Product;
import ai.mintpop.shop.util.I18nUtil;
import ai.mintpop.shop.util.StockUtil;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 商品详情响应体：在列表字段基础上多一段富文本详情。
 */
@Data
@AllArgsConstructor
public class ProductDetailResponse {

    /** 商品ID */
    private Long id;
    /** 商品名 */
    private String name;
    /** 商品短描述（卡片那行小字，详情页在详情为空时兜底展示） */
    private String description;
    /** 商品详情富文本HTML（已净化），空=未配置 */
    private String detail;
    /** 价格，单位美分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;
    /** 角标（按语言回退），空=不显示 */
    private String badge;
    /** 卡片主题色枚举：MINT/VIOLET/SKY/AMBER/ROSE */
    private String accent;
    /** 是否售罄（限库存且余量 ≤ 0）；不限库存永远 false */
    private Boolean soldOut;
    /** 低库存剩余数：仅当 0 < 余量 ≤ 5 时下发，其余为 null（不暴露原始库存） */
    private Integer stockLeft;

    public static ProductDetailResponse of(Product product, boolean english) {
        return new ProductDetailResponse(product.getId(),
                I18nUtil.pick(english, product.getNameEn(), product.getNameZh()),
                I18nUtil.pick(english, product.getDescriptionEn(), product.getDescriptionZh()),
                I18nUtil.pick(english, product.getDetailEn(), product.getDetailZh()),
                product.getPriceCents(), product.getImageUrl(),
                I18nUtil.pick(english, product.getBadgeEn(), product.getBadgeZh()),
                product.getAccent(),
                StockUtil.isSoldOut(product), StockUtil.lowStockLeft(product));
    }
}
