package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.request.AdminProductUpsertRequest;
import com.mintpop.shop.response.AdminProductResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductGroupMapper productGroupMapper;
    @InjectMocks
    private AdminProductService adminProductService;

    private AdminProductUpsertRequest request(long groupId) {
        return new AdminProductUpsertRequest(groupId, " 薄荷猫手办 ", "Mint Cat", null, "",
                "旗舰", " ", "MINT", 5900L, "  ", true);
    }

    @Test
    @DisplayName("新增：目标分组不存在抛 210003")
    void createWithMissingGroupRejected() {
        when(productGroupMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> adminProductService.createProduct(request(9L)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.GROUP_NOT_FOUND);
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    @DisplayName("新增：字段落库并把空白可选值归一为 null")
    void createNormalizesBlankOptionalFields() {
        when(productGroupMapper.selectById(1L)).thenReturn(new ProductGroup());

        adminProductService.createProduct(request(1L));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getNameZh()).isEqualTo("薄荷猫手办");
        assertThat(saved.getNameEn()).isEqualTo("Mint Cat");
        assertThat(saved.getDescriptionZh()).isNull();
        assertThat(saved.getDescriptionEn()).isNull();
        assertThat(saved.getBadgeEn()).isNull();
        assertThat(saved.getImageUrl()).isNull();
        assertThat(saved.getPriceCents()).isEqualTo(5900L);
        assertThat(saved.getOnSale()).isTrue();
    }

    @Test
    @DisplayName("编辑：商品不存在抛 210002")
    void updateMissingProductRejected() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> adminProductService.updateProduct(9L, request(1L)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("上下架：改状态并回传最新值")
    void setOnSaleUpdatesFlag() {
        Product product = new Product();
        product.setId(3L);
        product.setOnSale(true);
        when(productMapper.selectById(3L)).thenReturn(product);

        AdminProductResponse response = adminProductService.setOnSale(3L, false);

        assertThat(response.getOnSale()).isFalse();
        verify(productMapper).updateById(product);
    }
}
