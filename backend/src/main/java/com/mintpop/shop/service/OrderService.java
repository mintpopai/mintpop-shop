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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT);
        order.setUserId(userId);
        shopOrderMapper.insert(order);

        return new CreateOrderResponse(order.getOrderNo(), order.getAmountCents());
    }

    /**
     * 我的订单列表：按下单时间倒序，带商品名与中文状态。
     */
    public List<OrderItemResponse> listMyOrders(Long userId) {
        List<ShopOrder> orders = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .orderByDesc(ShopOrder::getCreatedAt)
                .orderByDesc(ShopOrder::getId));
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = orders.stream().map(ShopOrder::getProductId).collect(Collectors.toSet());
        // TODO(Task 4)：临时按中文列取值，订单接口整体 i18n 化时按请求语言取列。
        Map<Long, String> productNameById = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getNameZh));
        return orders.stream()
                .map(o -> new OrderItemResponse(
                        o.getOrderNo(),
                        productNameById.getOrDefault(o.getProductId(), "（商品已删除）"),
                        o.getQuantity(),
                        o.getAmountCents(),
                        o.getStatus().name(),
                        o.getStatus().getLabel(),
                        o.getCreatedAt()))
                .toList();
    }

    /** 订单号：MP + 时间戳 + 6 位随机数（骨架阶段单机够用） */
    private String generateOrderNo() {
        return "MP" + ORDER_NO_TS.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
}
