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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 下单服务。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ProductMapper productMapper;
    private final ShopOrderMapper shopOrderMapper;

    /**
     * 创建待支付订单：校验商品存在且上架，金额=单价×数量。
     */
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
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
        shopOrderMapper.insert(order);

        return new CreateOrderResponse(order.getOrderNo(), order.getAmountCents());
    }

    /** 订单号：MP + 时间戳 + 6 位随机数（骨架阶段单机够用） */
    private String generateOrderNo() {
        return "MP" + ORDER_NO_TS.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
}
