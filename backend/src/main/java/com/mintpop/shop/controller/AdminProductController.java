package com.mintpop.shop.controller;

import com.mintpop.shop.request.AdminProductOnSaleRequest;
import com.mintpop.shop.request.AdminProductUpsertRequest;
import com.mintpop.shop.response.AdminProductResponse;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.service.AdminProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端商品接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    /** 全部商品（含下架），可按分组过滤 */
    @GetMapping("/products")
    public ApiResponse<List<AdminProductResponse>> listProducts(
            @RequestParam(required = false) Long groupId) {
        return ApiResponse.success(adminProductService.listProducts(groupId));
    }

    /** 新增商品 */
    @PostMapping("/products")
    public ApiResponse<AdminProductResponse> createProduct(
            @Valid @RequestBody AdminProductUpsertRequest request) {
        return ApiResponse.success(adminProductService.createProduct(request));
    }

    /** 编辑商品 */
    @PutMapping("/products/{id}")
    public ApiResponse<AdminProductResponse> updateProduct(@PathVariable Long id,
            @Valid @RequestBody AdminProductUpsertRequest request) {
        return ApiResponse.success(adminProductService.updateProduct(id, request));
    }

    /** 上/下架 */
    @PutMapping("/products/{id}/on-sale")
    public ApiResponse<AdminProductResponse> setOnSale(@PathVariable Long id,
            @Valid @RequestBody AdminProductOnSaleRequest request) {
        return ApiResponse.success(adminProductService.setOnSale(id, request.getOnSale()));
    }
}
