package com.mintpop.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminOrderItemResponse;
import com.mintpop.shop.response.PageResponse;
import com.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private ShopOrderMapper shopOrderMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ShopUserMapper shopUserMapper;

    private AdminOrderService adminOrderService;

    @BeforeEach
    void setUp() {
        adminOrderService = new AdminOrderService(shopOrderMapper, productMapper, shopUserMapper,
                TestMessages.create());
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private ShopOrder order(long id, long productId, Long userId, OrderStatusEnum status) {
        ShopOrder o = new ShopOrder();
        o.setId(id);
        o.setOrderNo("mintpopshop_" + id);
        o.setProductId(productId);
        o.setUserId(userId);
        o.setQuantity(1);
        o.setAmountCents(5900L);
        o.setStatus(status);
        o.setCreatedAt(LocalDateTime.of(2026, 7, 30, 3, 0));
        return o;
    }

    private Product product(long id, String nameZh) {
        Product p = new Product();
        p.setId(id);
        p.setNameZh(nameZh);
        return p;
    }

    private ShopUser user(long id, String email) {
        ShopUser u = new ShopUser();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    @Test
    @DisplayName("分页组装：补商品名与买家邮箱，游客订单邮箱为空")
    void pageAssemblesProductNameAndBuyerEmail() {
        Page<ShopOrder> page = new Page<>(1, 20);
        page.setRecords(List.of(
                order(1L, 11L, 7L, OrderStatusEnum.PAID),
                order(2L, 11L, null, OrderStatusEnum.PENDING)));
        page.setTotal(2);
        when(shopOrderMapper.selectPage(any(), any())).thenReturn(page);
        when(productMapper.selectByIds(any())).thenReturn(List.of(product(11L, "薄荷猫手办")));
        when(shopUserMapper.selectByIds(any())).thenReturn(List.of(user(7L, "a@b.com")));

        PageResponse<AdminOrderItemResponse> result = adminOrderService.pageOrders(1, 20, null, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        AdminOrderItemResponse first = result.getRecords().get(0);
        assertThat(first.getProductName()).isEqualTo("薄荷猫手办");
        assertThat(first.getBuyerEmail()).isEqualTo("a@b.com");
        assertThat(first.getStatusLabel()).isEqualTo("已支付");
        assertThat(first.getCreatedAt().toString()).isEqualTo("2026-07-30T03:00:00Z");
        assertThat(result.getRecords().get(1).getBuyerEmail()).isNull();
    }

    @Test
    @DisplayName("商品已删除给占位文案")
    void deletedProductGetsPlaceholder() {
        Page<ShopOrder> page = new Page<>(1, 20);
        page.setRecords(List.of(order(1L, 99L, null, OrderStatusEnum.EXPIRED)));
        page.setTotal(1);
        when(shopOrderMapper.selectPage(any(), any())).thenReturn(page);
        when(productMapper.selectByIds(any())).thenReturn(List.of());

        PageResponse<AdminOrderItemResponse> result = adminOrderService.pageOrders(1, 20, null, null);

        assertThat(result.getRecords().get(0).getProductName()).isEqualTo("（商品已删除）");
    }

    @Test
    @DisplayName("非法状态参数抛 110002")
    void invalidStatusRejected() {
        assertThatThrownBy(() -> adminOrderService.pageOrders(1, 20, "NOT_A_STATUS", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PARAM_INVALID);
    }

    @Test
    @DisplayName("页码与页大小越界被钳制")
    void pageParamsClamped() {
        Page<ShopOrder> page = new Page<>(1, 100);
        page.setRecords(List.of());
        page.setTotal(0);
        when(shopOrderMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<AdminOrderItemResponse> result = adminOrderService.pageOrders(0, 9999, null, null);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(100);
    }
}
