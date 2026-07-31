package com.mintpop.shop.controller;

import com.mintpop.shop.request.AdminShipRequest;
import com.mintpop.shop.response.AdminShipmentItemResponse;
import com.mintpop.shop.response.AdminShipmentResponse;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.security.CurrentUserId;
import com.mintpop.shop.service.AdminShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端发货接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminShipmentController {

    private final AdminShipmentService adminShipmentService;

    /** 发货/重新发货：返回本次发货时间与邮件发送结果 */
    @PostMapping("/orders/{orderNo}/shipments")
    public ApiResponse<AdminShipmentResponse> ship(@CurrentUserId Long operatorUserId,
                                                   @PathVariable String orderNo,
                                                   @Valid @RequestBody AdminShipRequest request) {
        return ApiResponse.success(adminShipmentService.ship(
                orderNo, request.getContent(), request.getReason(), operatorUserId));
    }

    /** 某订单的发货历史（时间倒序） */
    @GetMapping("/orders/{orderNo}/shipments")
    public ApiResponse<List<AdminShipmentItemResponse>> listShipments(@PathVariable String orderNo) {
        return ApiResponse.success(adminShipmentService.listShipments(orderNo));
    }
}
