package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的订单列表项。
 */
@Data
@AllArgsConstructor
public class OrderItemResponse {

    /** 对外订单号 */
    private String orderNo;
    /** 商品名（商品已删除时给占位文案） */
    private String productName;
    /** 购买数量 */
    private Integer quantity;
    /** 订单金额，单位美分 */
    private Long amountCents;
    /** 订单状态（SCREAMING_SNAKE_CASE） */
    private String status;
    /** 订单状态展示文案（按请求语言） */
    private String statusLabel;
    /** 下单时间 */
    private LocalDateTime createdAt;
}
