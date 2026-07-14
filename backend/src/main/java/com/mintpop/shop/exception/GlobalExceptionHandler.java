package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把业务异常与未预期异常统一收口成 ApiResponse（HTTP 200），
 * 文案经 MessageSource 按当前请求语言解析。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        return ApiResponse.fail(e.getBizCode(), resolve(e.getBizCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String msg = messageSource.getMessage("biz.param-invalid.detail",
                new Object[]{detail}, LocaleContextHolder.getLocale());
        return new ApiResponse<>(BizCodeEnum.PARAM_INVALID.getCode(), null, msg);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.SYSTEM_ERROR, resolve(BizCodeEnum.SYSTEM_ERROR));
    }

    private String resolve(BizCodeEnum bizCode) {
        return messageSource.getMessage(bizCode.getMessageKey(), null, LocaleContextHolder.getLocale());
    }
}
