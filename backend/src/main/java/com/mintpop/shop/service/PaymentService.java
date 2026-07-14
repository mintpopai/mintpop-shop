package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.mintpop.shop.util.I18nUtil;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /** 我方支付子方式 → Stripe payment_method_types（品牌映射表，逐字一致） */
    private static final Map<String, String> PM_TYPE_MAPPING =
            Map.of("wxpay", "wechat_pay", "alipay", "alipay", "card", "card");

    private final ShopOrderMapper shopOrderMapper;
    private final ProductMapper productMapper;
    private final StripeGateway stripeGateway;
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
                order.getQuantity());
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
}
