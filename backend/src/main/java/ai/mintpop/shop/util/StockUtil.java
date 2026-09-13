package ai.mintpop.shop.util;

import ai.mintpop.shop.entity.Product;

/**
 * 前台库存展示口径：原始库存数不下发，只给「是否售罄」与「低库存时剩余几件」。
 */
public final class StockUtil {

    /** 低库存阈值：剩余 ≤ 此值才向买家露出具体数字制造紧迫感。管理端列表同值高亮，改这里要同步 */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private StockUtil() {
    }

    /** 售罄：限库存且余量 ≤ 0（入账竞态补扣可能出现负数，同样按售罄） */
    public static boolean isSoldOut(Product product) {
        return product.getStock() != null && product.getStock() <= 0;
    }

    /** 低库存剩余数：限库存且 0 < 余量 ≤ 阈值时返回余量，否则 null（不限、售罄、余量充足都不露数字） */
    public static Integer lowStockLeft(Product product) {
        Integer stock = product.getStock();
        return stock != null && stock > 0 && stock <= LOW_STOCK_THRESHOLD ? stock : null;
    }
}
