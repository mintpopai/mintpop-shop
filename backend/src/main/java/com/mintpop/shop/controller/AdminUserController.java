package com.mintpop.shop.controller;

import com.mintpop.shop.response.AdminUserResponse;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.PageResponse;
import com.mintpop.shop.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 注册用户分页（附订单数） */
    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> pageUsers(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(adminUserService.pageUsers(page, size));
    }
}
