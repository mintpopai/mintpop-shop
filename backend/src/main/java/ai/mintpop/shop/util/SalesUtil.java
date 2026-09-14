package ai.mintpop.shop.util;

import ai.mintpop.shop.entity.Product;

/**
 * 前台销量展示口径：展示销量（管理员手填基数）与实际销量（订单入账累加）相加，只下发一个总数。
 */
public final class SalesUtil {

    private SalesUtil() {
    }

    /** 销量 = displaySales + soldCount；任一为 null（理论上不会，列 NOT NULL DEFAULT 0）按 0 计 */
    public static int salesCount(Product product) {
        return nullToZero(product.getDisplaySales()) + nullToZero(product.getSoldCount());
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
