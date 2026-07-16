package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mintpop.shop.client.StripeGateway;
import com.mintpop.shop.config.OrderProperties;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.mapper.ShopOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpiryServiceTest {

    /** 纯单测无 MyBatis 容器，需手动注册实体元数据（与 OrderServiceTest 相同处理） */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShopOrder.class);
    }

    @Mock
    private ShopOrderMapper shopOrderMapper;
    @Mock
    private StripeGateway stripeGateway;
    private OrderProperties orderProperties;
    private OrderExpiryService orderExpiryService;

    @BeforeEach
    void setUp() {
        orderProperties = new OrderProperties();
        orderExpiryService = new OrderExpiryService(shopOrderMapper, stripeGateway, orderProperties);
    }

    private ShopOrder order(OrderStatusEnum status, LocalDateTime createdAt) {
        ShopOrder o = new ShopOrder();
        o.setId(7L);
        o.setOrderNo("mintpopshop_20260714120000123456");
        o.setStatus(status);
        o.setUserId(42L);
        o.setCreatedAt(createdAt);
        return o;
    }

    @Test
    @DisplayName("单笔：超时的 PENDING 订单条件置 EXPIRED，返回已超时")
    void expiresTimedOutPendingOrder() {
        ShopOrder timedOut = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));

        boolean expired = orderExpiryService.expireIfTimedOut(timedOut);

        assertThat(expired).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ShopOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(shopOrderMapper).update(isNull(), captor.capture());
        // 条件 UPDATE：where 锁单号 + 可过期状态（防与入账/取消竞态），set 置 EXPIRED
        assertThat(captor.getValue().getSqlSegment())
                .contains("order_no").contains("status").contains("IN");
        assertThat(captor.getValue().getSqlSet()).contains("status");
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(OrderStatusEnum.EXPIRED);
        // 未发起过支付（无交易号）：Stripe 侧无凭据可撤，不打网关
        verify(stripeGateway, never()).cancelPaymentIntent(anyString());
    }

    @Test
    @DisplayName("单笔：已发起支付的超时订单，本地过期后同步取消 Stripe 侧 PaymentIntent")
    void expireCancelsIntentAtGateway() {
        ShopOrder timedOut = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));
        timedOut.setPaymentProvider("stripe");
        timedOut.setPaymentTradeNo("pi_stale");

        boolean expired = orderExpiryService.expireIfTimedOut(timedOut);

        assertThat(expired).isTrue();
        verify(shopOrderMapper).update(isNull(), any());
        verify(stripeGateway).cancelPaymentIntent("pi_stale");
    }

    @Test
    @DisplayName("单笔：未超时订单不动库不打网关，返回未超时")
    void freshOrderNotExpired() {
        ShopOrder fresh = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusMinutes(5));

        assertThat(orderExpiryService.expireIfTimedOut(fresh)).isFalse();
        verify(shopOrderMapper, never()).update(isNull(), any());
        verify(stripeGateway, never()).cancelPaymentIntent(anyString());
    }

    @Test
    @DisplayName("单笔：createdAt 为空视为未超时（防御，不误杀）")
    void nullCreatedAtNotExpired() {
        ShopOrder noCreatedAt = order(OrderStatusEnum.PENDING, null);

        assertThat(orderExpiryService.expireIfTimedOut(noCreatedAt)).isFalse();
        verify(shopOrderMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("批量：先查出该用户的超时单，条件置 EXPIRED，再逐笔取消已发起支付的 intent")
    void batchExpiresByUser() {
        ShopOrder withIntent = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));
        withIntent.setPaymentTradeNo("pi_stale");
        ShopOrder withoutIntent = order(OrderStatusEnum.FAILED, LocalDateTime.now().minusHours(3));
        withoutIntent.setId(8L);
        withoutIntent.setOrderNo("mintpopshop_20260714110000654321");
        when(shopOrderMapper.selectList(any())).thenReturn(List.of(withIntent, withoutIntent));

        orderExpiryService.expireTimedOut(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ShopOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(shopOrderMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("user_id").contains("status").contains("IN").contains("created_at");
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(42L)
                .containsValue(OrderStatusEnum.EXPIRED);
        // 只有带交易号的单需要撤 Stripe 侧凭据
        verify(stripeGateway).cancelPaymentIntent("pi_stale");
        verify(stripeGateway, never()).cancelPaymentIntent("pi_none");
    }

    @Test
    @DisplayName("批量：没有超时单时不发 UPDATE 不打网关（列表高频入口零开销）")
    void batchNoTimedOutOrdersNoUpdate() {
        when(shopOrderMapper.selectList(any())).thenReturn(List.of());

        orderExpiryService.expireTimedOut(42L);

        verify(shopOrderMapper, never()).update(isNull(), any());
        verify(stripeGateway, never()).cancelPaymentIntent(anyString());
    }
}
