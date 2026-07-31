package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mintpop.shop.config.AppMailProperties;
import com.mintpop.shop.entity.OrderShipment;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.enumeration.ShipmentEmailStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.OrderShipmentMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminShipmentResponse;
import com.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminShipmentServiceTest {

    /** 纯单测无 MyBatis 容器，需手动注册实体元数据（与 OrderNotifyServiceTest 同处理） */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ShopOrder.class);
        TableInfoHelper.initTableInfo(assistant, OrderShipment.class);
    }

    @Mock
    private ShopOrderMapper shopOrderMapper;
    @Mock
    private OrderShipmentMapper orderShipmentMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ShopUserMapper shopUserMapper;
    @Mock
    private ShipmentMailSender shipmentMailSender;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AdminShipmentService service;

    @BeforeEach
    void setUp() {
        AppMailProperties mailProperties = new AppMailProperties();
        mailProperties.setDefaultLocale("en-US");
        service = new AdminShipmentService(shopOrderMapper, orderShipmentMapper, productMapper,
                shopUserMapper, shipmentMailSender, transactionTemplate, TestMessages.create(), mailProperties);
    }

    /** 事务模板直接执行回调，等价于「在事务里跑」 */
    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        when(transactionTemplate.execute(any())).thenAnswer(
                inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
    }

    /**
     * 与 {@link #runTransactionsInline()} 行为相同，但用 lenient 放宽严格 stub 校验：
     * 仅供「写库前置校验应使 persist() 根本不被调用」这类测试使用——正确实现下该 stub
     * 永远不会被触发，若用严格 when() 会被 Mockito 判为 UnnecessaryStubbingException；
     * 一旦实现退化成先写库再查询，这个 stub 就会被真正调用，从而暴露出插入动作。
     */
    @SuppressWarnings("unchecked")
    private void runTransactionsInlineLeniently() {
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(
                inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
    }

    private ShopOrder order(OrderStatusEnum status) {
        ShopOrder o = new ShopOrder();
        o.setId(7L);
        o.setOrderNo("mintpopshop_20260731120000123456");
        o.setProductId(3L);
        o.setUserId(11L);
        o.setQuantity(1);
        o.setAmountCents(1999L);
        o.setStatus(status);
        o.setCreatedAt(LocalDateTime.of(2026, 7, 31, 12, 0));
        return o;
    }

    private ShopUser buyer(String locale) {
        ShopUser u = new ShopUser();
        u.setId(11L);
        u.setEmail("buyer@example.com");
        u.setNickname("小明");
        u.setLocale(locale);
        return u;
    }

    private Product product() {
        Product p = new Product();
        p.setId(3L);
        p.setNameZh("会员账号");
        p.setNameEn("Membership");
        return p;
    }

    private void stubOrderAndBuyer(OrderStatusEnum status, String buyerLocale) {
        when(shopOrderMapper.selectOne(any())).thenReturn(order(status));
        when(shopUserMapper.selectById(11L)).thenReturn(buyer(buyerLocale));
    }

    @Test
    @DisplayName("首次发货：订单置已完成、插入发货记录、邮件成功后记 SENT")
    void firstShipmentCompletesOrder() {
        runTransactionsInline();
        stubOrderAndBuyer(OrderStatusEnum.PAID, "zh-CN");
        when(orderShipmentMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectById(3L)).thenReturn(product());
        when(shipmentMailSender.send(any(), eq("会员账号"), any(), eq("兑换码：ABC"), eq(Locale.forLanguageTag("zh-CN"))))
                .thenReturn(MailResult.ok());

        AdminShipmentResponse response = service.ship(
                "mintpopshop_20260731120000123456", "兑换码：ABC", null, 99L);

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatusEnum.COMPLETED);

        ArgumentCaptor<OrderShipment> insertCaptor = ArgumentCaptor.forClass(OrderShipment.class);
        verify(orderShipmentMapper).insert(insertCaptor.capture());
        OrderShipment inserted = insertCaptor.getValue();
        assertThat(inserted.getOrderId()).isEqualTo(7L);
        assertThat(inserted.getContent()).isEqualTo("兑换码：ABC");
        assertThat(inserted.getOperatorUserId()).isEqualTo(99L);
        assertThat(inserted.getEmailTo()).isEqualTo("buyer@example.com");
        // 先记失败：进程若在发信后崩溃，宁可让管理员多看一眼，也不误报「已发」
        assertThat(inserted.getEmailStatus()).isEqualTo(ShipmentEmailStatusEnum.FAILED);

        ArgumentCaptor<OrderShipment> updateCaptor = ArgumentCaptor.forClass(OrderShipment.class);
        verify(orderShipmentMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getEmailStatus()).isEqualTo(ShipmentEmailStatusEnum.SENT);

        assertThat(response.getEmailStatus()).isEqualTo("SENT");
        assertThat(response.getEmailError()).isNull();
        assertThat(response.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("邮件失败：订单仍置已完成，记录留 FAILED 与失败原因")
    void mailFailureDoesNotRollbackShipment() {
        runTransactionsInline();
        stubOrderAndBuyer(OrderStatusEnum.PAID, "zh-CN");
        when(orderShipmentMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectById(3L)).thenReturn(product());
        when(shipmentMailSender.send(any(), anyString(), any(), anyString(), any()))
                .thenReturn(MailResult.failed("connect timed out"));

        AdminShipmentResponse response = service.ship(
                "mintpopshop_20260731120000123456", "兑换码：ABC", null, 99L);

        verify(shopOrderMapper).updateById(any(ShopOrder.class));
        assertThat(response.getEmailStatus()).isEqualTo("FAILED");
        assertThat(response.getEmailError()).isEqualTo("connect timed out");

        ArgumentCaptor<OrderShipment> updateCaptor = ArgumentCaptor.forClass(OrderShipment.class);
        verify(orderShipmentMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getEmailError()).isEqualTo("connect timed out");
        assertThat(updateCaptor.getValue().getEmailStatus()).isEqualTo(ShipmentEmailStatusEnum.FAILED);
    }

    @Test
    @DisplayName("邮件状态回写失败：不外抛异常，仍按本次邮件结果返回")
    void markMailResultFailureDoesNotPropagate() {
        runTransactionsInline();
        stubOrderAndBuyer(OrderStatusEnum.PAID, "zh-CN");
        when(orderShipmentMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectById(3L)).thenReturn(product());
        when(shipmentMailSender.send(any(), eq("会员账号"), any(), eq("兑换码：ABC"), eq(Locale.forLanguageTag("zh-CN"))))
                .thenReturn(MailResult.ok());
        // 邮件已发出、发货也已落库之后，回写邮件状态这一步 DB 抖动抛异常
        when(orderShipmentMapper.updateById(any(OrderShipment.class)))
                .thenThrow(new RuntimeException("db connection reset"));

        AdminShipmentResponse response = service.ship(
                "mintpopshop_20260731120000123456", "兑换码：ABC", null, 99L);

        // 回写失败不能让「已经成功发货」这次操作对外报成失败，否则管理员会重发导致买家收到两封邮件
        assertThat(response.getEmailStatus()).isEqualTo("SENT");
        assertThat(response.getEmailError()).isNull();
        assertThat(response.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("商品名查询异常：不能已经写库——锁住「查询必须在写库之前」的顺序")
    void productNameLookupFailureLeavesNoWrite() {
        // 必须真正让事务回调执行（而非让 transactionTemplate.execute 停在未 stub 的默认 null），
        // 否则不论查询顺序对错，persist() 都不会真的调用 insert/updateById，测试会失去鉴别力；
        // 用 lenient 版本是因为正确实现下这个 stub 根本不会被触发（查询在写库前就已抛出）
        runTransactionsInlineLeniently();
        stubOrderAndBuyer(OrderStatusEnum.PAID, "zh-CN");
        when(orderShipmentMapper.selectCount(any())).thenReturn(0L);
        // 模拟事务提交后仍可能触发的那次 DB 读抖动：若查询被错误地挪回写库之后，
        // 这次异常会在发货记录已经插入之后才抛出，留下一条「幽灵记录」
        when(productMapper.selectById(3L)).thenThrow(new RuntimeException("db connection reset"));

        assertThatThrownBy(() -> service.ship(
                "mintpopshop_20260731120000123456", "兑换码：ABC", null, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db connection reset");

        // 商品名查询必须在写库之前完成：查询一炸，两处写库都不能发生，不留幽灵记录
        verify(orderShipmentMapper, never()).insert(any(OrderShipment.class));
        verify(shopOrderMapper, never()).updateById(any(ShopOrder.class));
    }

    @Test
    @DisplayName("已发过货再发：缺原因直接报错，不写库不发信")
    void reshipRequiresReason() {
        stubOrderAndBuyer(OrderStatusEnum.COMPLETED, "zh-CN");
        when(orderShipmentMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.ship(
                "mintpopshop_20260731120000123456", "兑换码：DEF", "  ", 99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.SHIPMENT_REASON_REQUIRED);

        // any() 在此与 insert(T)/insert(Collection<T>) 两个重载歧义（mybatis-plus-core 3.5.17 新增批量插入），显式指定类型消歧
        verify(orderShipmentMapper, never()).insert(any(OrderShipment.class));
        verify(shipmentMailSender, never()).send(any(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("已发过货再发：带原因可发，原因入库")
    void reshipWithReasonSucceeds() {
        runTransactionsInline();
        stubOrderAndBuyer(OrderStatusEnum.COMPLETED, "zh-CN");
        when(orderShipmentMapper.selectCount(any())).thenReturn(1L);
        when(productMapper.selectById(3L)).thenReturn(product());
        when(shipmentMailSender.send(any(), anyString(), any(), anyString(), any()))
                .thenReturn(MailResult.ok());

        service.ship("mintpopshop_20260731120000123456", "兑换码：DEF", "上次发错卡密", 99L);

        ArgumentCaptor<OrderShipment> insertCaptor = ArgumentCaptor.forClass(OrderShipment.class);
        verify(orderShipmentMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getReason()).isEqualTo("上次发错卡密");
    }

    @Test
    @DisplayName("未支付订单不可发货")
    void pendingOrderNotShippable() {
        when(shopOrderMapper.selectOne(any())).thenReturn(order(OrderStatusEnum.PENDING));

        assertThatThrownBy(() -> service.ship(
                "mintpopshop_20260731120000123456", "兑换码：ABC", null, 99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ORDER_NOT_SHIPPABLE);
    }

    @Test
    @DisplayName("订单不存在报 ORDER_NOT_FOUND")
    void missingOrder() {
        when(shopOrderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.ship("nope", "兑换码：ABC", null, 99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("买家语言偏好为空时回退配置的默认语言，商品名按该语言取")
    void fallsBackToConfiguredDefaultLocale() {
        runTransactionsInline();
        stubOrderAndBuyer(OrderStatusEnum.PAID, null);
        when(orderShipmentMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectById(3L)).thenReturn(product());
        Locale configuredDefault = Locale.forLanguageTag("en-US");
        when(shipmentMailSender.send(any(), eq("Membership"), any(), anyString(), eq(configuredDefault)))
                .thenReturn(MailResult.ok());

        service.ship("mintpopshop_20260731120000123456", "Code: ABC", null, 99L);

        // setUp() 里 mailProperties.defaultLocale = en-US；买家 locale 为 null 时必须用它，
        // 而不是当前请求（管理员发货请求）的语言——二者在生产中恒为 zh-CN，与买家无关
        verify(shipmentMailSender).send(any(), eq("Membership"), any(), eq("Code: ABC"), eq(configuredDefault));
    }

    @Test
    @DisplayName("发货历史按时间倒序，带操作人邮箱")
    void listsShipmentHistory() {
        when(shopOrderMapper.selectOne(any())).thenReturn(order(OrderStatusEnum.COMPLETED));
        OrderShipment first = shipment(1L, "第一次", null, ShipmentEmailStatusEnum.SENT);
        OrderShipment second = shipment(2L, "第二次", "发错了", ShipmentEmailStatusEnum.FAILED);
        when(orderShipmentMapper.selectList(any())).thenReturn(List.of(second, first));
        ShopUser operator = buyer("zh-CN");
        operator.setId(99L);
        operator.setEmail("admin@mintpop.ai");
        when(shopUserMapper.selectByIds(any())).thenReturn(List.of(operator));

        var history = service.listShipments("mintpopshop_20260731120000123456");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getContent()).isEqualTo("第二次");
        assertThat(history.get(0).getReason()).isEqualTo("发错了");
        assertThat(history.get(0).getEmailStatus()).isEqualTo("FAILED");
        assertThat(history.get(0).getOperatorEmail()).isEqualTo("admin@mintpop.ai");
        assertThat(history.get(1).getContent()).isEqualTo("第一次");
    }

    private OrderShipment shipment(long id, String content, String reason, ShipmentEmailStatusEnum status) {
        OrderShipment s = new OrderShipment();
        s.setId(id);
        s.setOrderId(7L);
        s.setContent(content);
        s.setReason(reason);
        s.setOperatorUserId(99L);
        s.setEmailTo("buyer@example.com");
        s.setEmailStatus(status);
        s.setCreatedAt(LocalDateTime.of(2026, 7, 31, 12, 0));
        return s;
    }
}
