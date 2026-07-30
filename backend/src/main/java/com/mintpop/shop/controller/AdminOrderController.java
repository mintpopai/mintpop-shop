package com.mintpop.shop.controller;

import com.mintpop.shop.response.AdminOrderItemResponse;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.PageResponse;
import com.mintpop.shop.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /** 全量订单分页：可按状态筛选、按订单号前缀搜索 */
    @GetMapping("/orders")
    public ApiResponse<PageResponse<AdminOrderItemResponse>> pageOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminOrderService.pageOrders(page, size, status, keyword));
    }
}
