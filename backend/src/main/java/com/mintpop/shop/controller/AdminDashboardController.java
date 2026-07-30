package com.mintpop.shop.controller;

import com.mintpop.shop.response.AdminDashboardResponse;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端概览接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /** 概览：核心统计 + 最近订单 */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.success(adminDashboardService.getDashboard());
    }
}
