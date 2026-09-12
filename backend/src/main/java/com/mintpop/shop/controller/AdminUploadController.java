package com.mintpop.shop.controller;

import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.ImageUploadResponse;
import com.mintpop.shop.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端上传接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUploadController {

    private final ImageUploadService imageUploadService;

    /** 上传图片到 R2，返回公开 URL；商品主图与富文本插图共用 */
    @PostMapping("/uploads/images")
    public ApiResponse<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(imageUploadService.upload(file));
    }
}
