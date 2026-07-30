package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminDashboardResponse;
import com.mintpop.shop.response.AdminOrderItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private ShopOrderMapper shopOrderMapper;
    @Mock
    private ShopUserMapper shopUserMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private AdminOrderService adminOrderService;
    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("统计组装：SUM 的 DECIMAL 返回值正确转 long，最近订单走统一组装")
    void assemblesStats() {
        // 两次 selectMaps：累计营收、今日营收
        when(shopOrderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("revenue", new BigDecimal("117800"))))
                .thenReturn(List.of(Map.of("revenue", new BigDecimal("5900"))));
        // 两次 selectCount：累计订单、今日订单
        when(shopOrderMapper.selectCount(any())).thenReturn(30L, 2L);
        when(shopUserMapper.selectCount(any())).thenReturn(12L);
        when(productMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(9L);
        Page<ShopOrder> recentPage = new Page<>(1, 10);
        recentPage.setRecords(List.of());
        when(shopOrderMapper.selectPage(any(), any())).thenReturn(recentPage);
        List<AdminOrderItemResponse> assembled = List.of();
        when(adminOrderService.assemble(anyList())).thenReturn(assembled);

        AdminDashboardResponse dashboard = adminDashboardService.getDashboard();

        assertThat(dashboard.getTotalRevenueCents()).isEqualTo(117800L);
        assertThat(dashboard.getTodayRevenueCents()).isEqualTo(5900L);
        assertThat(dashboard.getTotalOrderCount()).isEqualTo(30L);
        assertThat(dashboard.getTodayOrderCount()).isEqualTo(2L);
        assertThat(dashboard.getUserCount()).isEqualTo(12L);
        assertThat(dashboard.getOnSaleProductCount()).isEqualTo(9L);
        assertThat(dashboard.getRecentOrders()).isSameAs(assembled);
    }

    @Test
    @DisplayName("空库：聚合无行时营收回退 0")
    void emptyAggregationFallsBackToZero() {
        when(shopOrderMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        when(shopOrderMapper.selectCount(any())).thenReturn(0L);
        when(shopUserMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Page<ShopOrder> recentPage = new Page<>(1, 10);
        recentPage.setRecords(List.of());
        when(shopOrderMapper.selectPage(any(), any())).thenReturn(recentPage);
        when(adminOrderService.assemble(anyList())).thenReturn(List.of());

        AdminDashboardResponse dashboard = adminDashboardService.getDashboard();

        assertThat(dashboard.getTotalRevenueCents()).isZero();
        assertThat(dashboard.getTodayRevenueCents()).isZero();
    }
}
