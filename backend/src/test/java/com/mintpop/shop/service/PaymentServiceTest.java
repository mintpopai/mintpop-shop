package com.mintpop.shop.service;

import com.mintpop.shop.client.StripeGateway;
import com.mintpop.shop.config.PaymentProperties;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.response.CheckoutInfoResponse;
import com.mintpop.shop.response.PaymentIntentResponse;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private ShopOrderMapper shopOrderMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private StripeGateway stripeGateway;
    private PaymentProperties properties;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        properties.setSecretKey("test-secret");
        properties.setPublishableKey("test-publishable");
        paymentService = new PaymentService(shopOrderMapper, productMapper, stripeGateway, properties);
        // 断言中文商品名，需固定请求语言（与 OrderServiceTest 相同处理）
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private ShopOrder pendingOrder() {
        ShopOrder o = new ShopOrder();
        o.setId(7L);
        o.setOrderNo("MP20260714120000123456");
        o.setProductId(1L);
        o.setQuantity(2);
        o.setAmountCents(11800L);
        o.setStatus(OrderStatusEnum.PENDING);
        o.setUserId(42L);
        return o;
    }

    private Product product() {
        Product p = new Product();
        p.setId(1L);
        p.setNameZh("薄荷精灵盲盒");
        p.setNameEn("Mint Sprite Blind Box");
        return p;
    }

    private PaymentIntent intent(String id, String status, String clientSecret) {
        PaymentIntent pi = new PaymentIntent();
        pi.setId(id);
        pi.setStatus(status);
        pi.setClientSecret(clientSecret);
        return pi;
    }

    @Test
    @DisplayName("收银台信息：已配置时返回 stripe 通道与 publishable key")
    void checkoutInfoConfigured() {
        CheckoutInfoResponse resp = paymentService.checkoutInfo();
        assertThat(resp.getMethods()).containsExactly("stripe");
        assertThat(resp.getStripePublishableKey()).isEqualTo("test-publishable");
    }

    @Test
    @DisplayName("收银台信息：未配置时通道为空、不下发 key")
    void checkoutInfoNotConfigured() {
        properties.setSecretKey("");
        CheckoutInfoResponse resp = paymentService.checkoutInfo();
        assertThat(resp.getMethods()).isEmpty();
        assertThat(resp.getStripePublishableKey()).isNull();
    }

    @Test
    @DisplayName("首次发起：创建 PaymentIntent（方式映射 wxpay→wechat_pay），并把交易号落库")
    void createIntentFirstTime() {
        when(shopOrderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(productMapper.selectById(1L)).thenReturn(product());
        when(stripeGateway.createPaymentIntent(eq("MP20260714120000123456"), eq(11800L),
                anyString(), anyList()))
                .thenReturn(intent("pi_123", "requires_payment_method", "pi_123_secret"));

        PaymentIntentResponse resp = paymentService.getOrCreateIntent(42L, "MP20260714120000123456");

        assertThat(resp.getClientSecret()).isEqualTo("pi_123_secret");
        assertThat(resp.getAmountCents()).isEqualTo(11800L);
        assertThat(resp.getCurrency()).isEqualTo("CNY");
        assertThat(resp.getProductName()).isEqualTo("薄荷精灵盲盒");

        // 映射表逐字：card,alipay,wxpay → card,alipay,wechat_pay
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> typesCaptor = ArgumentCaptor.forClass(List.class);
        verify(stripeGateway).createPaymentIntent(anyString(), anyLong(), anyString(),
                typesCaptor.capture());
        assertThat(typesCaptor.getValue()).containsExactly("card", "alipay", "wechat_pay");

        // 交易号与处理方落库
        ArgumentCaptor<ShopOrder> patchCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).updateById(patchCaptor.capture());
        assertThat(patchCaptor.getValue().getPaymentTradeNo()).isEqualTo("pi_123");
        assertThat(patchCaptor.getValue().getPaymentProvider()).isEqualTo("stripe");
    }

    @Test
    @DisplayName("supported-types 为空时回退 card")
    void emptySupportedTypesFallsBackToCard() {
        properties.setSupportedTypes("");
        when(shopOrderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(productMapper.selectById(1L)).thenReturn(product());
        when(stripeGateway.createPaymentIntent(anyString(), anyLong(), anyString(), anyList()))
                .thenReturn(intent("pi_123", "requires_payment_method", "pi_123_secret"));

        paymentService.getOrCreateIntent(42L, "MP20260714120000123456");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> typesCaptor = ArgumentCaptor.forClass(List.class);
        verify(stripeGateway).createPaymentIntent(anyString(), anyLong(), anyString(),
                typesCaptor.capture());
        assertThat(typesCaptor.getValue()).containsExactly("card");
    }

    @Test
    @DisplayName("旧单续付：已有交易号则检索复用，不再创建")
    void reuseExistingIntent() {
        ShopOrder order = pendingOrder();
        order.setPaymentProvider("stripe");
        order.setPaymentTradeNo("pi_old");
        when(shopOrderMapper.selectOne(any())).thenReturn(order);
        when(productMapper.selectById(1L)).thenReturn(product());
        when(stripeGateway.retrievePaymentIntent("pi_old"))
                .thenReturn(intent("pi_old", "requires_payment_method", "pi_old_secret"));

        PaymentIntentResponse resp = paymentService.getOrCreateIntent(42L, "MP20260714120000123456");

        assertThat(resp.getClientSecret()).isEqualTo("pi_old_secret");
        verify(stripeGateway, never()).createPaymentIntent(anyString(), anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("订单不存在或不属于当前用户：410001")
    void orderNotFoundOrNotOwned() {
        when(shopOrderMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> paymentService.getOrCreateIntent(42L, "MP-nope"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ORDER_NOT_FOUND);

        ShopOrder othersOrder = pendingOrder();
        othersOrder.setUserId(99L);
        when(shopOrderMapper.selectOne(any())).thenReturn(othersOrder);
        assertThatThrownBy(() -> paymentService.getOrCreateIntent(42L, "MP20260714120000123456"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("已支付订单不可再发起支付：410002")
    void paidOrderNotPayable() {
        ShopOrder order = pendingOrder();
        order.setStatus(OrderStatusEnum.PAID);
        when(shopOrderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> paymentService.getOrCreateIntent(42L, "MP20260714120000123456"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ORDER_NOT_PAYABLE);
    }
}
