package ai.mintpop.shop.util;

import ai.mintpop.shop.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前台库存展示口径：不限库存永不售罄也不提示；0 与负数售罄；1~5 提示剩余；6 起不露数字。
 */
class StockUtilTest {

    private static Product product(Integer stock) {
        Product p = new Product();
        p.setStock(stock);
        return p;
    }

    @ParameterizedTest(name = "stock={0} → soldOut={1}, stockLeft={2}")
    @CsvSource(nullValues = "null", value = {
            "null, false, null",
            "0,    true,  null",
            "-2,   true,  null",
            "1,    false, 1",
            "5,    false, 5",
            "6,    false, null",
            "999,  false, null",
    })
    @DisplayName("售罄与低库存提示的边界")
    void soldOutAndLowStockBoundaries(Integer stock, boolean soldOut, Integer stockLeft) {
        Product p = product(stock);
        assertThat(StockUtil.isSoldOut(p)).isEqualTo(soldOut);
        assertThat(StockUtil.lowStockLeft(p)).isEqualTo(stockLeft);
    }
}
