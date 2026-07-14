package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体（表 shop_order）。
 */
@Data
@TableName("shop_order")
public class ShopOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外订单号，唯一 */
    private String orderNo;
    /** 商品ID */
    private Long productId;
    /** 购买数量 */
    private Integer quantity;
    /** 订单金额，单位分 */
    private Long amountCents;
    /** 订单状态 */
    private OrderStatusEnum status;
    /** 支付处理方（stripe；未发起支付时为空） */
    private String paymentProvider;
    /** 网关交易号（Stripe PaymentIntent ID） */
    private String paymentTradeNo;
    /** 支付完成时间 */
    private LocalDateTime paidAt;
    /** 下单用户ID（shop_user.id；存量游客订单为空） */
    private Long userId;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
