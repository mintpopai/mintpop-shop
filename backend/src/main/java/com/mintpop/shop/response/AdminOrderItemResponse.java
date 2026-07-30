package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 管理端订单列表项（比用户侧多买家与支付通道信息）。
 */
@Data
@AllArgsConstructor
public class AdminOrderItemResponse {

    /** 对外订单号 */
    private String orderNo;
    /** 商品名（按请求语言，商品已删除时给占位文案） */
    private String productName;
    /** 买家邮箱（存量游客订单为空） */
    private String buyerEmail;
    /** 购买数量 */
    private Integer quantity;
    /** 订单金额，单位美分 */
    private Long amountCents;
    /** 订单状态（SCREAMING_SNAKE_CASE） */
    private String status;
    /** 订单状态展示文案（按请求语言） */
    private String statusLabel;
    /** 支付处理方（未发起支付为空） */
    private String paymentProvider;
    /** 下单时间（ISO-8601 UTC 带 Z，展示时区由前端定） */
    private Instant createdAt;
    /** 支付完成时间，未支付为空 */
    private Instant paidAt;
}
