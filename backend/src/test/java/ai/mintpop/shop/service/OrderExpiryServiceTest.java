package ai.mintpop.shop.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import ai.mintpop.shop.client.StripeGateway;
import ai.mintpop.shop.config.OrderProperties;
import ai.mintpop.shop.entity.ShopOrder;
import ai.mintpop.shop.enumeration.OrderStatusEnum;
import ai.mintpop.shop.mapper.ShopOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private StockService stockService;
    @Mock
    private TransactionTemplate transactionTemplate;
    private OrderProperties orderProperties;
    private OrderExpiryService orderExpiryService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderProperties = new OrderProperties();
        orderExpiryService = new OrderExpiryService(shopOrderMapper, stripeGateway, orderProperties,
                stockService, transactionTemplate);
        // 事务模板直接执行回调；未超时用例不进事务，故 lenient
        lenient().when(transactionTemplate.execute(any())).thenAnswer(
                inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
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
        when(shopOrderMapper.update(isNull(), any())).thenReturn(1);
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
        verify(stockService).release(timedOut);
    }

    @Test
    @DisplayName("单笔：已发起支付的超时订单，本地过期后同步取消 Stripe 侧 PaymentIntent")
    void expireCancelsIntentAtGateway() {
        when(shopOrderMapper.update(isNull(), any())).thenReturn(1);
        ShopOrder timedOut = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));
        timedOut.setPaymentProvider("stripe");
        timedOut.setPaymentTradeNo("pi_stale");

        boolean expired = orderExpiryService.expireIfTimedOut(timedOut);

        assertThat(expired).isTrue();
        verify(shopOrderMapper).update(isNull(), any());
        verify(stripeGateway).cancelPaymentIntent("pi_stale");
    }

    @Test
    @DisplayName("单笔：条件 UPDATE 0 行（已被入账/取消抢先）则不归还库存，但仍按超时返回 true")
    void expireNoRowsDoesNotRelease() {
        ShopOrder timedOut = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));
        when(shopOrderMapper.update(isNull(), any())).thenReturn(0);

        assertThat(orderExpiryService.expireIfTimedOut(timedOut)).isTrue();
        verify(stockService, never()).release(any());
    }

    @Test
    @DisplayName("单笔：未超时订单不动库不打网关，返回未超时")
    void freshOrderNotExpired() {
        ShopOrder fresh = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusMinutes(5));

        assertThat(orderExpiryService.expireIfTimedOut(fresh)).isFalse();
        verify(shopOrderMapper, never()).update(isNull(), any());
        verify(stripeGateway, never()).cancelPaymentIntent(anyString());
        verify(stockService, never()).release(any());
    }

    @Test
    @DisplayName("单笔：createdAt 为空视为未超时（防御，不误杀）")
    void nullCreatedAtNotExpired() {
        ShopOrder noCreatedAt = order(OrderStatusEnum.PENDING, null);

        assertThat(orderExpiryService.expireIfTimedOut(noCreatedAt)).isFalse();
        verify(shopOrderMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("剩余支付秒数：新单约等于时限减已过时间（前端倒计时用）")
    void remainingSecondsForFreshOrder() {
        ShopOrder fresh = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusMinutes(10));

        long remaining = orderExpiryService.remainingSeconds(fresh);

        // 默认时限 30 分钟，已过 10 分钟 → 剩约 20 分钟（容忍执行耗时的秒级误差）
        assertThat(remaining).isBetween(19 * 60L, 20 * 60L);
    }

    @Test
    @DisplayName("剩余支付秒数：已超时订单为 0，不出负数")
    void remainingSecondsZeroWhenTimedOut() {
        ShopOrder timedOut = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));

        assertThat(orderExpiryService.remainingSeconds(timedOut)).isZero();
    }

    @Test
    @DisplayName("剩余支付秒数：createdAt 为空按整个时限算（与不误杀口径一致）")
    void remainingSecondsFullWhenNoCreatedAt() {
        ShopOrder noCreatedAt = order(OrderStatusEnum.PENDING, null);

        assertThat(orderExpiryService.remainingSeconds(noCreatedAt)).isEqualTo(30 * 60L);
    }

    @Test
    @DisplayName("批量：查出该用户的超时单后逐单条件置 EXPIRED，只对真正生效的单归还库存并撤凭据")
    void batchExpiresByUser() {
        ShopOrder withIntent = order(OrderStatusEnum.PENDING, LocalDateTime.now().minusHours(2));
        withIntent.setPaymentTradeNo("pi_stale");
        ShopOrder settledMeanwhile = order(OrderStatusEnum.FAILED, LocalDateTime.now().minusHours(3));
        settledMeanwhile.setId(8L);
        settledMeanwhile.setOrderNo("mintpopshop_20260714110000654321");
        when(shopOrderMapper.selectList(any())).thenReturn(List.of(withIntent, settledMeanwhile));
        // 第一单置 EXPIRED 生效；第二单在查出与 UPDATE 之间被 webhook 置 PAID，0 行
        when(shopOrderMapper.update(isNull(), any())).thenReturn(1, 0);

        orderExpiryService.expireTimedOut(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ShopOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(shopOrderMapper, times(2)).update(isNull(), captor.capture());
        // 逐单条件 UPDATE：where 锁单号 + 可过期状态，不再按 user_id 批量改
        LambdaUpdateWrapper<ShopOrder> first = captor.getAllValues().get(0);
        assertThat(first.getSqlSegment()).contains("order_no").contains("status").contains("IN");
        assertThat(first.getParamNameValuePairs())
                .containsValue(withIntent.getOrderNo())
                .containsValue(OrderStatusEnum.EXPIRED);
        // 库存只归还真正过期的那单；被入账的单绝不能把库存加回去
        verify(stockService).release(withIntent);
        verify(stockService, never()).release(settledMeanwhile);
        // Stripe 侧撤凭据仍按「有交易号」判断
        verify(stripeGateway).cancelPaymentIntent("pi_stale");
    }

    @Test
    @DisplayName("批量：没有超时单时不发 UPDATE 不打网关（列表高频入口零开销）")
    void batchNoTimedOutOrdersNoUpdate() {
        when(shopOrderMapper.selectList(any())).thenReturn(List.of());

        orderExpiryService.expireTimedOut(42L);

        verify(shopOrderMapper, never()).update(isNull(), any());
        verify(stripeGateway, never()).cancelPaymentIntent(anyString());
        verify(stockService, never()).release(any());
    }
}
