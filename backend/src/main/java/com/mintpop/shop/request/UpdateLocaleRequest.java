package com.mintpop.shop.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存语言偏好请求。取值白名单在 UserService.SUPPORTED_LOCALES。
 */
@Data
public class UpdateLocaleRequest {

    /** 语言偏好（BCP47：zh-CN/en-US） */
    @NotBlank(message = "{biz.validation.locale-required}")
    private String locale;
}
