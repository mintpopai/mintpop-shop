package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.request.CreateOrderRequest;
import com.mintpop.shop.response.CreateOrderResponse;
import com.mintpop.shop.response.OrderItemResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
    private OrderService orderService;

    private Product onSaleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setName("薄荷精灵盲盒");
        p.setPriceCents(5900L);
        p.setOnSale(true);
        return p;
    }

    private ShopOrder order(long productId, int quantity, long amountCents) {
        ShopOrder o = new ShopOrder();
        o.setOrderNo("MP20260713120000123456");
        o.setProductId(productId);
        o.setQuantity(quantity);
        o.setAmountCents(amountCents);
        o.setStatus(OrderStatusEnum.PENDING_PAYMENT);
        o.setUserId(42L);
        o.setCreatedAt(LocalDateTime.of(2026, 7, 13, 12, 0));
        return o;
    }

    @Test
    @DisplayName("下单成功：金额=单价×数量，状态待支付，绑定当前用户")
    void createOrderSuccess() {
        when(productMapper.selectById(1L)).thenReturn(onSaleProduct());

        CreateOrderResponse resp = orderService.createOrder(42L, new CreateOrderRequest(1L, 2));

        assertThat(resp.getAmountCents()).isEqualTo(11800L);
        assertThat(resp.getOrderNo()).startsWith("MP");

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatusEnum.PENDING_PAYMENT);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("商品不存在：抛业务异常 210001")
    void productNotFoundThrows() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(42L, new CreateOrderRequest(99L, 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("商品已下架：抛业务异常 210001")
    void productOffSaleThrows() {
        Product offSale = onSaleProduct();
        offSale.setOnSale(false);
        when(productMapper.selectById(1L)).thenReturn(offSale);

        assertThatThrownBy(() -> orderService.createOrder(42L, new CreateOrderRequest(1L, 1)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("我的订单：查询按当前用户过滤，带商品名与中文状态，商品已删除给占位文案")
    void listMyOrdersJoinsProductName() {
        when(shopOrderMapper.selectList(any())).thenReturn(
                List.of(order(1L, 2, 11800L), order(99L, 1, 5900L)));
        when(productMapper.selectByIds(anyCollection())).thenReturn(List.of(onSaleProduct()));

        List<OrderItemResponse> result = orderService.listMyOrders(42L);

        // 锁定查询条件确实按 userId 过滤（防止退化成查全表）。
        // 注：MyBatis-Plus 的条件参数惰性收集，先渲染 SQL 段（getSqlSegment）参数表才有值。
        ArgumentCaptor<LambdaQueryWrapper<ShopOrder>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(shopOrderMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ShopOrder> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("user_id");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(42L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProductName()).isEqualTo("薄荷精灵盲盒");
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.get(0).getStatusLabel()).isEqualTo("待支付");
        assertThat(result.get(1).getProductName()).isEqualTo("（商品已删除）");
    }

    @Test
    @DisplayName("我的订单：无订单返回空列表，不查商品")
    void listMyOrdersEmpty() {
        when(shopOrderMapper.selectList(any())).thenReturn(List.of());

        assertThat(orderService.listMyOrders(42L)).isEmpty();
        verify(productMapper, never()).selectByIds(anyCollection());
    }
}
