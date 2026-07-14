package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(TestMessages.create());

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("业务异常转为对应业务码，中文请求返回中文文案")
    void bizExceptionMappedToBizCode() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        ApiResponse<Void> resp = handler.handleBizException(
                new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));
        assertThat(resp.getCode()).isEqualTo(210001);
        assertThat(resp.getMsg()).isEqualTo("商品不存在或已下架");
        assertThat(resp.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("英文请求返回英文文案")
    void bizExceptionEnglishMsg() {
        LocaleContextHolder.setLocale(Locale.US);
        ApiResponse<Void> resp = handler.handleBizException(
                new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));
        assertThat(resp.getCode()).isEqualTo(210001);
        assertThat(resp.getMsg()).isEqualTo("Product not found or off sale");
    }

    @Test
    @DisplayName("未预期异常转为系统错误码")
    void unexpectedExceptionMappedToSystemError() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        ApiResponse<Void> resp = handler.handleUnexpected(new IllegalStateException("boom"));
        assertThat(resp.getCode()).isEqualTo(110001);
        assertThat(resp.getMsg()).isEqualTo("系统繁忙，请稍后重试");
    }

    @Test
    @DisplayName("成功工厂方法 code 为 0")
    void successFactoryReturnsZeroCode() {
        ApiResponse<String> resp = ApiResponse.success("data");
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isEqualTo("data");
        assertThat(resp.isSuccess()).isTrue();
    }
}
