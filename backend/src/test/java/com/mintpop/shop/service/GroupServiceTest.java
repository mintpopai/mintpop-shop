package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.response.GroupWithProductsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private ProductGroupMapper productGroupMapper;
    @Mock
    private ProductMapper productMapper;
    @InjectMocks
    private GroupService groupService;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private ProductGroup group(long id, String nameZh, String nameEn) {
        ProductGroup g = new ProductGroup();
        g.setId(id);
        g.setNameZh(nameZh);
        g.setNameEn(nameEn);
        return g;
    }

    private Product product(long id, long groupId, String nameZh, String nameEn) {
        Product p = new Product();
        p.setId(id);
        p.setGroupId(groupId);
        p.setNameZh(nameZh);
        p.setNameEn(nameEn);
        p.setPriceCents(5900L);
        p.setOnSale(true);
        return p;
    }

    @Test
    @DisplayName("商品按分组归组，空分组返回空商品列表，中文请求出中文名")
    void productsGroupedByGroupId() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(productGroupMapper.selectList(any())).thenReturn(List.of(
                group(1L, "盲盒系列", "Blind Boxes"), group(2L, "手办摆件", "Figures & Decor")));
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(11L, 1L, "薄荷精灵盲盒", "Mint Sprite Blind Box"),
                product(12L, 1L, "云朵萌宠盲盒", "Cloud Pets Blind Box")));

        List<GroupWithProductsResponse> result = groupService.listGroupsWithProducts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("盲盒系列");
        assertThat(result.get(0).getProducts()).hasSize(2);
        assertThat(result.get(0).getProducts().get(0).getName()).isEqualTo("薄荷精灵盲盒");
        assertThat(result.get(1).getProducts()).isEmpty();
    }

    @Test
    @DisplayName("英文请求出英文名，英文列为空白时回退中文")
    void englishLocalePicksEnglishWithFallback() {
        LocaleContextHolder.setLocale(Locale.US);
        when(productGroupMapper.selectList(any())).thenReturn(List.of(
                group(1L, "盲盒系列", "Blind Boxes")));
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(11L, 1L, "薄荷精灵盲盒", "Mint Sprite Blind Box"),
                product(12L, 1L, "云朵萌宠盲盒", "")));

        List<GroupWithProductsResponse> result = groupService.listGroupsWithProducts();

        assertThat(result.get(0).getName()).isEqualTo("Blind Boxes");
        assertThat(result.get(0).getProducts().get(0).getName()).isEqualTo("Mint Sprite Blind Box");
        assertThat(result.get(0).getProducts().get(1).getName()).isEqualTo("云朵萌宠盲盒");
    }
}
