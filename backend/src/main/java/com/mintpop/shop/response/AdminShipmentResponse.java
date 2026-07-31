package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 本次发货的结果：发货本身一定成功（否则抛业务异常），邮件是否发出去看 emailStatus。
 */
@Data
@AllArgsConstructor
public class AdminShipmentResponse {

    /** 发货时间（绝对时刻，ISO-8601 UTC） */
    private Instant shippedAt;
    /** 邮件发送结果：SENT/FAILED */
    private String emailStatus;
    /** 邮件失败原因（成功为空） */
    private String emailError;
}
