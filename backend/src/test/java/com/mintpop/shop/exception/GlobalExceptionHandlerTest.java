package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常转为对应业务码")
    void bizExceptionMappedToBizCode() {
        ApiResponse<Void> resp = handler.handleBizException(
                new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));
        assertThat(resp.getCode()).isEqualTo(210001);
        assertThat(resp.getMsg()).isEqualTo("商品不存在或已下架");
        assertThat(resp.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("未预期异常转为系统错误码")
    void unexpectedExceptionMappedToSystemError() {
        ApiResponse<Void> resp = handler.handleUnexpected(new IllegalStateException("boom"));
        assertThat(resp.getCode()).isEqualTo(110001);
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
