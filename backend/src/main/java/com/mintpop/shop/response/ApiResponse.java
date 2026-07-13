package com.mintpop.shop.response;

import com.mintpop.shop.enumeration.BizCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回结构：code=0 表示成功，非 0 为业务失败；HTTP 状态码一律 200。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    /** 业务状态码：0=成功，非0=失败（6 位分段业务码） */
    private Integer code;
    /** 业务数据 */
    private T data;
    /** 描述/错误信息 */
    private String msg;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, null, null);
    }

    public static <T> ApiResponse<T> fail(BizCodeEnum bizCode) {
        return new ApiResponse<>(bizCode.getCode(), null, bizCode.getMessage());
    }

    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
