package ai.mintpop.shop.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import ai.mintpop.shop.entity.Product;
import ai.mintpop.shop.entity.ShopOrder;
import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.enumeration.StockHoldEnum;
import ai.mintpop.shop.exception.BizException;
import ai.mintpop.shop.mapper.ProductMapper;
import ai.mintpop.shop.mapper.ShopOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    /** 纯单测无 MyBatis 容器，需手动注册实体元数据，lambda 条件才能渲染出 SQL 段与参数 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShopOrder.class);
    }

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ShopOrderMapper shopOrderMapper;
    @InjectMocks
    private StockService stockService;

    private Product product(Integer stock) {
        Product p = new Product();
        p.setId(1L);
        p.setStock(stock);
        return p;
    }

    private ShopOrder order(StockHoldEnum hold) {
        ShopOrder o = new ShopOrder();
        o.setId(7L);
        o.setProductId(1L);
        o.setQuantity(2);
        o.setStockHold(hold);
        return o;
    }

    @Test
    @DisplayName("预占：不限库存的商品不碰库存，返回 NONE")
    void reserveUnlimitedSkipsDeduction() {
        assertThat(stockService.reserve(product(null), 2)).isEqualTo(StockHoldEnum.NONE);
        verify(productMapper, never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("预占：限库存商品按数量条件扣减，成功返回 HELD")
    void reserveDeductsWhenEnough() {
        when(productMapper.reserveStock(1L, 2)).thenReturn(1);

        assertThat(stockService.reserve(product(5), 2)).isEqualTo(StockHoldEnum.HELD);
    }

    @Test
    @DisplayName("预占：条件 UPDATE 影响 0 行即库存不足，抛 210009")
    void reserveInsufficientThrows() {
        when(productMapper.reserveStock(1L, 2)).thenReturn(0);

        assertThatThrownBy(() -> stockService.reserve(product(1), 2))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PRODUCT_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("归还：订单 HELD→RELEASED 条件 UPDATE 生效才把数量加回商品")
    void releaseOnlyWhenHeld() {
        when(shopOrderMapper.update(isNull(), any())).thenReturn(1);

        stockService.release(order(StockHoldEnum.HELD));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ShopOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(shopOrderMapper).update(isNull(), captor.capture());
        // where 锁 id + 当前状态 HELD（幂等钥匙），set 置 RELEASED
        assertThat(captor.getValue().getSqlSegment()).contains("id").contains("stock_hold");
        assertThat(captor.getValue().getSqlSet()).contains("stock_hold");
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(StockHoldEnum.HELD)
                .containsValue(StockHoldEnum.RELEASED);
        verify(productMapper).releaseStock(1L, 2);
    }

    @Test
    @DisplayName("归还：状态不是 HELD（未预占或已归还）时 0 行，不动商品库存——重复归还幂等")
    void releaseIdempotentWhenNotHeld() {
        when(shopOrderMapper.update(isNull(), any())).thenReturn(0);

        stockService.release(order(StockHoldEnum.RELEASED));

        verify(productMapper, never()).releaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("成交：HELD→CONSUMED 一步成功，不再查单不动库存（预占本身就是实扣）")
    void consumeHeldTransitionsOnly() {
        when(shopOrderMapper.update(isNull(), any())).thenReturn(1);

        stockService.consume(order(StockHoldEnum.HELD));

        verify(shopOrderMapper, never()).selectById(anyLong());
        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("成交：预占已被取消/过期归还（RELEASED），钱已收则补扣并置 CONSUMED")
    void consumeReleasedRededucts() {
        // 第一次 HELD→CONSUMED 0 行，重查是 RELEASED，第二次 RELEASED→CONSUMED 1 行
        when(shopOrderMapper.update(isNull(), any())).thenReturn(0, 1);
        when(shopOrderMapper.selectById(7L)).thenReturn(order(StockHoldEnum.RELEASED));

        stockService.consume(order(StockHoldEnum.HELD));

        verify(productMapper).deductStock(1L, 2);
    }

    @Test
    @DisplayName("成交：从未预占（NONE，不限库存商品）或已成交的单不动库存")
    void consumeNoneUntouched() {
        when(shopOrderMapper.update(isNull(), any())).thenReturn(0);
        when(shopOrderMapper.selectById(7L)).thenReturn(order(StockHoldEnum.NONE));

        stockService.consume(order(StockHoldEnum.NONE));

        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("成交：RELEASED→CONSUMED 被并发抢先（0 行）时不重复补扣")
    void consumeReleasedRaceDoesNotDoubleDeduct() {
        when(shopOrderMapper.update(isNull(), any())).thenReturn(0, 0);
        when(shopOrderMapper.selectById(7L)).thenReturn(order(StockHoldEnum.RELEASED));

        stockService.consume(order(StockHoldEnum.HELD));

        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }
}
