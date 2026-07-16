package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.mintpop.shop.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ProductMapper productMapper;
    private final ShopOrderMapper shopOrderMapper;
    private final OrderExpiryService orderExpiryService;
    private final MessageSource messageSource;

    /**
     * 创建待支付订单：校验商品存在且上架，金额=单价×数量，绑定当前登录用户。
     */
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || !Boolean.TRUE.equals(product.getOnSale())) {
            throw new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE);
        }

        ShopOrder order = new ShopOrder();
        order.setOrderNo(generateOrderNo());
        order.setProductId(product.getId());
        order.setQuantity(request.getQuantity());
        order.setAmountCents(product.getPriceCents() * request.getQuantity());
        order.setStatus(OrderStatusEnum.PENDING);
        order.setUserId(userId);
        shopOrderMapper.insert(order);

        return new CreateOrderResponse(order.getOrderNo(), order.getAmountCents());
    }

    /**
     * 我的订单列表：按下单时间倒序，商品名与状态标签按请求语言下发。
     */
    public List<OrderItemResponse> listMyOrders(Long userId) {
        // 懒惰过期：先把该用户超时未支付的订单批量置 EXPIRED，列表读到的即是最新状态
        orderExpiryService.expireTimedOut(userId);
        List<ShopOrder> orders = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .orderByDesc(ShopOrder::getCreatedAt)
                .orderByDesc(ShopOrder::getId));
        if (orders.isEmpty()) {
            return List.of();
        }
        Locale locale = LocaleContextHolder.getLocale();
        boolean english = I18nUtil.isEnglish();
        Set<Long> productIds = orders.stream().map(ShopOrder::getProductId).collect(Collectors.toSet());
        Map<Long, String> productNameById = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId,
                        p -> I18nUtil.pick(english, p.getNameEn(), p.getNameZh())));
        String deletedPlaceholder = messageSource.getMessage("order.product-deleted", null, locale);
        return orders.stream()
                .map(o -> new OrderItemResponse(
                        o.getOrderNo(),
                        productNameById.getOrDefault(o.getProductId(), deletedPlaceholder),
                        o.getQuantity(),
                        o.getAmountCents(),
                        o.getStatus().name(),
                        messageSource.getMessage(o.getStatus().getLabelKey(), null, locale),
                        o.getCreatedAt()))
                .toList();
    }

    /** 订单号：mintpopshop_ + 时间戳 + 6 位随机数（骨架阶段单机够用）；
     * 前缀带业务线全名，多业务共用 Stripe 账户时人眼可辨来源 */
    private String generateOrderNo() {
        return "mintpopshop_" + ORDER_NO_TS.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
}
