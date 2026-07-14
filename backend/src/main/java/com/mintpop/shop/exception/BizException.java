package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import lombok.Getter;

/**
 * 业务异常：携带业务码，由全局异常处理器统一转为 ApiResponse。
 */
@Getter
public class BizException extends RuntimeException {

    private final BizCodeEnum bizCode;

    public BizException(BizCodeEnum bizCode) {
        super(bizCode.name());
        this.bizCode = bizCode;
    }
}
