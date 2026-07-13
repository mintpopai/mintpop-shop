package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.request.CreateOrderRequest;
import com.mintpop.shop.response.CreateOrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ShopOrderMapper shopOrderMapper;
    @InjectMocks
    private OrderService orderService;

    private Product onSaleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setPriceCents(5900L);
        p.setOnSale(true);
        return p;
    }

    @Test
    @DisplayName("下单成功：金额=单价×数量，状态为待支付")
    void createOrderSuccess() {
        when(productMapper.selectById(1L)).thenReturn(onSaleProduct());

        CreateOrderResponse resp = orderService.createOrder(new CreateOrderRequest(1L, 2));

        assertThat(resp.getAmountCents()).isEqualTo(11800L);
        assertThat(resp.getOrderNo()).startsWith("MP");

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatusEnum.PENDING_PAYMENT);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("商品不存在：抛业务异常 210001")
    void productNotFoundThrows() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(99L, 1)))
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

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(1L, 1)))
                .isInstanceOf(BizException.class);
    }
}
