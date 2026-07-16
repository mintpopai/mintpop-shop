package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mintpop.shop.client.FeishuBotClient;
import com.mintpop.shop.config.NotifyProperties;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
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
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNotifyServiceTest {

    /** 纯单测无 MyBatis 容器，需手动注册实体元数据（与 PaymentServiceTest 相同处理） */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShopOrder.class);
    }

    @Mock
    private ShopOrderMapper shopOrderMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ShopUserMapper shopUserMapper;
    @Mock
    private FeishuBotClient feishuBotClient;
    private NotifyProperties properties;
    private OrderNotifyService service;

    @BeforeEach
    void setUp() {
        properties = new NotifyProperties();
        properties.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test");
        service = new OrderNotifyService(shopOrderMapper, productMapper, shopUserMapper,
                properties, feishuBotClient);
    }

    private ShopOrder paidOrder() {
        ShopOrder o = new ShopOrder();
        o.setId(7L);
        o.setOrderNo("MP20260716120000123456");
        o.setProductId(1L);
        o.setQuantity(2);
        o.setAmountCents(11800L);
        o.setStatus(OrderStatusEnum.PAID);
        o.setUserId(42L);
        o.setPaymentTradeNo("pi_123");
        o.setPaidAt(LocalDateTime.of(2026, 7, 16, 14, 32, 5));
        return o;
    }

    private Product product() {
        Product p = new Product();
        p.setId(1L);
        p.setNameZh("薄荷精灵盲盒");
        return p;
    }

    private ShopUser user() {
        ShopUser u = new ShopUser();
        u.setId(42L);
        u.setNickname("月白");
        u.setEmail("yuebai@example.com");
        return u;
    }

    @Test
    @DisplayName("入账通知：组装完整卡片字段（顺序固定）并发送")
    void notifySendsCardWithFields() {
        when(shopOrderMapper.selectOne(any())).thenReturn(paidOrder());
        when(productMapper.selectById(1L)).thenReturn(product());
        when(shopUserMapper.selectById(42L)).thenReturn(user());

        service.notifyOrderPaid("MP20260716120000123456");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashMap<String, String>> captor =
                ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(feishuBotClient).sendCard(eq("MintPop Shop 新订单已支付"), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                entry("订单号", "MP20260716120000123456"),
                entry("商品", "薄荷精灵盲盒 × 2"),
                entry("金额", "$118.00"),
                entry("买家", "月白 (yuebai@example.com)"),
                entry("交易号", "pi_123"),
                entry("支付时间", "2026-07-16 14:32:05"));
    }

    @Test
    @DisplayName("未配置 webhook：静默跳过，不查库不发送")
    void notConfiguredSkipsSilently() {
        properties.setWebhookUrl("");
        service.notifyOrderPaid("MP20260716120000123456");
        verifyNoInteractions(shopOrderMapper, productMapper, shopUserMapper, feishuBotClient);
    }

    @Test
    @DisplayName("查无此单：不发送、不抛异常")
    void unknownOrderSkipped() {
        when(shopOrderMapper.selectOne(any())).thenReturn(null);
        assertThatCode(() -> service.notifyOrderPaid("MP-nope")).doesNotThrowAnyException();
        verify(feishuBotClient, never()).sendCard(anyString(), any());
    }

    @Test
    @DisplayName("查无用户（存量边界）：买家兜底显示「未知买家」")
    void missingUserFallsBackToUnknownBuyer() {
        when(shopOrderMapper.selectOne(any())).thenReturn(paidOrder());
        when(productMapper.selectById(1L)).thenReturn(product());
        when(shopUserMapper.selectById(42L)).thenReturn(null);

        service.notifyOrderPaid("MP20260716120000123456");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashMap<String, String>> captor =
                ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(feishuBotClient).sendCard(anyString(), captor.capture());
        assertThat(captor.getValue()).containsEntry("买家", "未知买家");
    }

    @Test
    @DisplayName("客户端发送抛异常：只记日志不外抛（绝不影响入账主流程）")
    void clientFailureSwallowed() {
        when(shopOrderMapper.selectOne(any())).thenReturn(paidOrder());
        when(productMapper.selectById(1L)).thenReturn(product());
        when(shopUserMapper.selectById(42L)).thenReturn(user());
        doThrow(new IllegalStateException("飞书挂了")).when(feishuBotClient)
                .sendCard(anyString(), any());

        assertThatCode(() -> service.notifyOrderPaid("MP20260716120000123456"))
                .doesNotThrowAnyException();
    }
}
