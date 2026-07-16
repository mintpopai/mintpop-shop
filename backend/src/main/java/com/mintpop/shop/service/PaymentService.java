package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mintpop.shop.client.StripeGateway;
import com.mintpop.shop.client.StripeWebhookEvent;
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
import com.mintpop.shop.response.VerifyOrderResponse;
import com.mintpop.shop.util.I18nUtil;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 支付服务：后端只有单一 stripe 通道（PaymentIntent 模式），
 * 子方式（微信/支付宝/银行卡）只存在于前端展示层，不进本层任何参数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 支付处理方标识（落库 payment_provider） */
    static final String PROVIDER_STRIPE = "stripe";

    /** webhook 只处理的两个事件类型 */
    private static final String EVENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String EVENT_FAILED = "payment_intent.payment_failed";

    /** 我方支付子方式 → Stripe payment_method_types（品牌映射表，逐字一致） */
    private static final Map<String, String> PM_TYPE_MAPPING =
            Map.of("wxpay", "wechat_pay", "alipay", "alipay", "card", "card");

    private final ShopOrderMapper shopOrderMapper;
    private final ProductMapper productMapper;
    private final StripeGateway stripeGateway;
    private final OrderExpiryService orderExpiryService;
    private final PaymentProperties paymentProperties;

    /** 收银台信息：未配置时下发空通道列表，前端据此禁用支付入口 */
    public CheckoutInfoResponse checkoutInfo() {
        if (!paymentProperties.isConfigured()) {
            return new CheckoutInfoResponse(List.of(), null);
        }
        return new CheckoutInfoResponse(List.of(PROVIDER_STRIPE),
                paymentProperties.getPublishableKey());
    }

    /**
     * 懒创建/复用支付意图：待支付（含上次尝试失败）订单进入支付页时调用。
     * 首次创建后把交易号落库；再次进入按交易号检索复用，新旧单同一条路。
     */
    public PaymentIntentResponse getOrCreateIntent(Long userId, String orderNo) {
        ShopOrder order = requireOwnOrder(userId, orderNo);
        if (order.getStatus() != OrderStatusEnum.PENDING
                && order.getStatus() != OrderStatusEnum.FAILED) {
            throw new BizException(BizCodeEnum.ORDER_NOT_PAYABLE);
        }
        // 懒惰过期：超时未支付订单在此拦下，不再下发/创建支付凭据
        if (orderExpiryService.expireIfTimedOut(order)) {
            throw new BizException(BizCodeEnum.ORDER_NOT_PAYABLE);
        }
        Product product = productMapper.selectById(order.getProductId());
        String subject = product == null
                ? order.getOrderNo()
                : I18nUtil.pick(I18nUtil.isEnglish(), product.getNameEn(), product.getNameZh());

        PaymentIntent intent;
        if (order.getPaymentTradeNo() == null) {
            intent = stripeGateway.createPaymentIntent(order.getOrderNo(),
                    order.getAmountCents(), subject, resolvePaymentMethodTypes());
            ShopOrder patch = new ShopOrder();
            patch.setId(order.getId());
            patch.setPaymentProvider(PROVIDER_STRIPE);
            patch.setPaymentTradeNo(intent.getId());
            shopOrderMapper.updateById(patch);
        } else {
            intent = stripeGateway.retrievePaymentIntent(order.getPaymentTradeNo());
            if ("canceled".equals(intent.getStatus())) {
                // intent 在网关侧被取消（罕见）：无法续付，引导重新下单
                throw new BizException(BizCodeEnum.PAYMENT_GATEWAY_ERROR);
            }
        }
        return new PaymentIntentResponse(order.getOrderNo(), intent.getClientSecret(),
                order.getAmountCents(), paymentProperties.getCurrency(), subject,
                order.getQuantity(), orderExpiryService.remainingSeconds(order));
    }

    /** 配置的子方式经映射表转 Stripe 类型；为空一律回退 card（品牌硬约定） */
    private List<String> resolvePaymentMethodTypes() {
        List<String> types = Arrays.stream(paymentProperties.getSupportedTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(PM_TYPE_MAPPING::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return types.isEmpty() ? List.of("card") : types;
    }

    /** 按单号取订单并校验归属；查无或非本人一律 410001（不泄露他人单号存在性） */
    private ShopOrder requireOwnOrder(Long userId, String orderNo) {
        ShopOrder order = shopOrderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo));
        if (order == null || !Objects.equals(userId, order.getUserId())) {
            throw new BizException(BizCodeEnum.ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 处理 webhook 事件（成单唯一真相源）。除签名错误外这里不抛业务异常：
     * 查无此单/重放/无关事件一律静默成功，由控制器回 2xx 止住 Stripe 重试。
     */
    public void handleWebhook(StripeWebhookEvent event) {
        if (event.orderNo() == null) {
            return;
        }
        // 业务线认领：Stripe 事件是账户级广播，别的业务的事件静默跳过；
        // 无 product 标记的事件（打标前创建的旧 intent）放行，交给后续查单兜底
        if (event.product() != null
                && !paymentProperties.getProductCode().equals(event.product())) {
            log.debug("非本业务线事件，跳过 product={} orderNo={}", event.product(), event.orderNo());
            return;
        }
        switch (event.type()) {
            case EVENT_SUCCEEDED -> settlePaid(event.orderNo(), event.intentId(),
                    event.amountMinorUnit(), event.currency());
            case EVENT_FAILED -> markFailed(event.orderNo());
            default -> { /* 无关事件：忽略 */ }
        }
    }

    /**
     * 主动向网关核实并推进状态（前端轮询用），与 webhook 共用 settlePaid，天然幂等。
     */
    public VerifyOrderResponse verify(Long userId, String orderNo) {
        ShopOrder order = requireOwnOrder(userId, orderNo);
        boolean settleable = order.getStatus() == OrderStatusEnum.PENDING
                || order.getStatus() == OrderStatusEnum.FAILED;
        if (settleable && order.getPaymentTradeNo() != null) {
            PaymentIntent intent = stripeGateway.retrievePaymentIntent(order.getPaymentTradeNo());
            if ("succeeded".equals(intent.getStatus())) {
                settlePaid(orderNo, intent.getId(), intent.getAmount(), intent.getCurrency());
                order = requireOwnOrder(userId, orderNo);
                return new VerifyOrderResponse(order.getOrderNo(), order.getStatus().name());
            }
        }
        // 网关未成功才考虑懒惰过期：入账优先，钱已收的订单不允许被判过期
        if (settleable && orderExpiryService.expireIfTimedOut(order)) {
            order = requireOwnOrder(userId, orderNo);
        }
        return new VerifyOrderResponse(order.getOrderNo(), order.getStatus().name());
    }

    /** 取消订单：仅待支付/支付失败可取消（条件 UPDATE，0 行即状态不允许） */
    public void cancel(Long userId, String orderNo) {
        requireOwnOrder(userId, orderNo);
        int rows = shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED)
                .set(ShopOrder::getStatus, OrderStatusEnum.CANCELLED));
        if (rows == 0) {
            throw new BizException(BizCodeEnum.ORDER_NOT_CANCELLABLE);
        }
    }

    /**
     * 幂等入账：先校验 provider 与金额/币种（最小单位整数，直接相等比较），
     * 再条件 UPDATE 置 PAID——允许来源 PENDING/FAILED/CANCELLED/EXPIRED（重试成功、
     * 取消或懒惰过期竞态时钱已收必须入账），影响 0 行即已处理过，静默返回。
     */
    private void settlePaid(String orderNo, String intentId, Long amountMinorUnit, String currency) {
        ShopOrder order = shopOrderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo));
        if (order == null) {
            log.warn("入账查无此单，忽略 orderNo={}", orderNo);
            return;
        }
        if (order.getPaymentProvider() != null
                && !PROVIDER_STRIPE.equals(order.getPaymentProvider())) {
            log.warn("入账 provider 不符，拒绝 orderNo={} provider={}", orderNo,
                    order.getPaymentProvider());
            return;
        }
        if (!Objects.equals(amountMinorUnit, order.getAmountCents())
                || currency == null
                || !paymentProperties.getCurrency().equalsIgnoreCase(currency)) {
            log.warn("入账金额/币种与订单不符，拒绝 orderNo={} amount={} currency={} 订单金额={}",
                    orderNo, amountMinorUnit, currency, order.getAmountCents());
            return;
        }
        int rows = shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED,
                        OrderStatusEnum.CANCELLED, OrderStatusEnum.EXPIRED)
                .set(ShopOrder::getStatus, OrderStatusEnum.PAID)
                .set(ShopOrder::getPaidAt, LocalDateTime.now())
                .set(ShopOrder::getPaymentProvider, PROVIDER_STRIPE)
                .set(ShopOrder::getPaymentTradeNo, intentId));
        if (rows == 0) {
            log.info("入账重放（已处理过），忽略 orderNo={}", orderNo);
        }
    }

    /** 支付尝试失败：仅 PENDING → FAILED（FAILED 仍可续付，不影响重试成功后入账） */
    private void markFailed(String orderNo) {
        shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .eq(ShopOrder::getStatus, OrderStatusEnum.PENDING)
                .set(ShopOrder::getStatus, OrderStatusEnum.FAILED));
    }
}
