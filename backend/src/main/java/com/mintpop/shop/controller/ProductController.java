package com.mintpop.shop.controller;

import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.ProductDetailResponse;
import com.mintpop.shop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（公开，游客可看）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 商品详情（仅上架商品可见） */
    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.success(productService.getOnSaleProduct(id));
    }
}
