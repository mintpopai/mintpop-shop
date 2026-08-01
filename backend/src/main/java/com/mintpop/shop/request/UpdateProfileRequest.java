package com.mintpop.shop.request;

import com.mintpop.shop.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存个人档案请求（设置页一次提交昵称 + 语言偏好）。
 * 语言取值白名单在 UserService.SUPPORTED_LOCALES；邮箱/角色不在此维护。
 */
@Data
public class UpdateProfileRequest {

    /** 昵称（去首尾空格后 1~30 字符） */
    @NotBlank(message = "{biz.validation.nickname-required}")
    @Size(max = UserService.NICKNAME_MAX_LENGTH, message = "{biz.validation.nickname-max}")
    private String nickname;

    /** 语言偏好（BCP47：zh-CN/en-US） */
    @NotBlank(message = "{biz.validation.locale-required}")
    private String locale;
}
