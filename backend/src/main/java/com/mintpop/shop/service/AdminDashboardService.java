package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 管理端概览服务：核心统计 + 最近订单。
 * 营收口径：状态 PAID/COMPLETED 的订单金额之和；「今日」按 UTC 日（库内时间即 UTC）。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    /** 最近订单条数 */
    private static final int RECENT_ORDER_COUNT = 10;

    /** 计入营收的订单状态 */
    private static final List<String> REVENUE_STATUSES =
            List.of(OrderStatusEnum.PAID.getValue(), OrderStatusEnum.COMPLETED.getValue());

    private final ShopOrderMapper shopOrderMapper;
    private final ShopUserMapper shopUserMapper;
    private final ProductMapper productMapper;
    private final AdminOrderService adminOrderService;

    public AdminDashboardResponse getDashboard() {
        LocalDateTime todayStartUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay();

        long totalRevenue = sumRevenue(null);
        long todayRevenue = sumRevenue(todayStartUtc);
        long totalOrders = shopOrderMapper.selectCount(null);
        long todayOrders = shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .ge(ShopOrder::getCreatedAt, todayStartUtc));
        long users = shopUserMapper.selectCount(null);
        long onSaleProducts = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, true));

        // 最近订单只取一页、不做 count（Page 第三参 false）
        List<ShopOrder> recent = shopOrderMapper.selectPage(
                Page.of(1, RECENT_ORDER_COUNT, false),
                new LambdaQueryWrapper<ShopOrder>()
                        .orderByDesc(ShopOrder::getCreatedAt)
                        .orderByDesc(ShopOrder::getId))
                .getRecords();

        return new AdminDashboardResponse(totalRevenue, totalOrders, todayOrders, todayRevenue,
                users, onSaleProducts, adminOrderService.assemble(recent));
    }

    /** 营收聚合：from 为空算累计，非空算该时刻起（含） */
    private long sumRevenue(LocalDateTime from) {
        QueryWrapper<ShopOrder> wrapper = new QueryWrapper<ShopOrder>()
                .select("IFNULL(SUM(amount_cents), 0) AS revenue")
                .in("status", REVENUE_STATUSES);
        if (from != null) {
            wrapper.ge("created_at", from);
        }
        List<Map<String, Object>> rows = shopOrderMapper.selectMaps(wrapper);
        if (rows.isEmpty() || rows.get(0) == null) {
            return 0L;
        }
        Object revenue = rows.get(0).get("revenue");
        return revenue == null ? 0L : ((Number) revenue).longValue();
    }
}
