package com.mintpop.shop.controller;

import com.mintpop.shop.request.UpdateLocaleRequest;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.MeResponse;
import com.mintpop.shop.security.CurrentUserId;
import com.mintpop.shop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    /** 当前登录用户档案（未登录由安全链返回 401） */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    /** 保存语言偏好（站内切换语言时调用，跨设备保持一致） */
    @PutMapping("/me/locale")
    public ApiResponse<Void> updateLocale(@CurrentUserId Long userId,
                                          @Valid @RequestBody UpdateLocaleRequest request) {
        userService.updateLocale(userId, request.getLocale());
        return ApiResponse.success();
    }
}
