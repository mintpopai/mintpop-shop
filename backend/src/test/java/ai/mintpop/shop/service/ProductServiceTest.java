package ai.mintpop.shop.service;

import ai.mintpop.shop.entity.Product;
import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.exception.BizException;
import ai.mintpop.shop.mapper.ProductMapper;
import ai.mintpop.shop.response.ProductDetailResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;
    @InjectMocks
    private ProductService productService;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Product onSaleProduct() {
        Product p = new Product();
        p.setId(11L);
        p.setGroupId(1L);
        p.setNameZh("薄荷精灵盲盒");
        p.setNameEn("Mint Sprite Blind Box");
        p.setDescriptionZh("经典款");
        p.setDescriptionEn("Classic");
        p.setDetailZh("<p>中文详情</p>");
        p.setDetailEn("<p>English detail</p>");
        p.setBadgeZh("经典款");
        p.setAccent("MINT");
        p.setPriceCents(5900L);
        p.setOnSale(true);
        return p;
    }

    @Test
    @DisplayName("上架商品：中文请求出中文详情")
    void returnsChineseDetail() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(productMapper.selectById(11L)).thenReturn(onSaleProduct());

        ProductDetailResponse result = productService.getOnSaleProduct(11L);

        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getName()).isEqualTo("薄荷精灵盲盒");
        assertThat(result.getDetail()).isEqualTo("<p>中文详情</p>");
        assertThat(result.getPriceCents()).isEqualTo(5900L);
        assertThat(result.getAccent()).isEqualTo("MINT");
    }

    @Test
    @DisplayName("英文请求出英文详情，英文详情为空时回退中文")
    void englishDetailFallsBackToChinese() {
        LocaleContextHolder.setLocale(Locale.US);
        Product product = onSaleProduct();
        product.setDetailEn("  ");
        when(productMapper.selectById(11L)).thenReturn(product);

        ProductDetailResponse result = productService.getOnSaleProduct(11L);

        assertThat(result.getName()).isEqualTo("Mint Sprite Blind Box");
        assertThat(result.getDetail()).isEqualTo("<p>中文详情</p>");
    }

    @Test
    @DisplayName("商品不存在：抛 210002")
    void missingProductRejected() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> productService.getOnSaleProduct(9L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("已下架商品：同样报不存在，不泄露下架商品的存在")
    void offSaleProductLooksMissing() {
        Product product = onSaleProduct();
        product.setOnSale(false);
        when(productMapper.selectById(11L)).thenReturn(product);

        assertThatThrownBy(() -> productService.getOnSaleProduct(11L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("售罄商品仍可查到详情，soldOut 为真且不露剩余数")
    void soldOutProductStillVisible() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        Product soldOut = onSaleProduct();
        soldOut.setStock(0);
        when(productMapper.selectById(11L)).thenReturn(soldOut);

        ProductDetailResponse result = productService.getOnSaleProduct(11L);

        assertThat(result.getSoldOut()).isTrue();
        assertThat(result.getStockLeft()).isNull();
    }

    @Test
    @DisplayName("低库存商品下发剩余数")
    void lowStockProductExposesStockLeft() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        Product low = onSaleProduct();
        low.setStock(3);
        when(productMapper.selectById(11L)).thenReturn(low);

        ProductDetailResponse result = productService.getOnSaleProduct(11L);

        assertThat(result.getSoldOut()).isFalse();
        assertThat(result.getStockLeft()).isEqualTo(3);
    }

    @Test
    @DisplayName("详情下发的销量 = 展示销量 + 实际销量，不单独暴露两部分")
    void salesCountIsDisplayPlusActual() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        Product p = onSaleProduct();
        p.setDisplaySales(120);
        p.setSoldCount(7);
        when(productMapper.selectById(11L)).thenReturn(p);

        ProductDetailResponse result = productService.getOnSaleProduct(11L);

        assertThat(result.getSalesCount()).isEqualTo(127);
    }
}
