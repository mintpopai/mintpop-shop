package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 管理端概览响应体：核心统计 + 最近订单。
 * 营收口径：状态为 PAID/COMPLETED 的订单金额之和；「今日」按 UTC 日（全链路 UTC）。
 */
@Data
@AllArgsConstructor
public class AdminDashboardResponse {

    /** 累计营收，单位美分 */
    private Long totalRevenueCents;
    /** 累计订单数（全状态） */
    private Long totalOrderCount;
    /** 今日（UTC）订单数 */
    private Long todayOrderCount;
    /** 今日（UTC）营收，单位美分 */
    private Long todayRevenueCents;
    /** 注册用户数 */
    private Long userCount;
    /** 在售商品数 */
    private Long onSaleProductCount;
    /** 最近订单（最多 10 条） */
    private List<AdminOrderItemResponse> recentOrders;
}
