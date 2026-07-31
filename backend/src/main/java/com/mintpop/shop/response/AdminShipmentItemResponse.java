package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 管理端发货历史项。
 */
@Data
@AllArgsConstructor
public class AdminShipmentItemResponse {

    /** 发货记录ID */
    private Long id;
    /** 发货内容文本 */
    private String content;
    /** 本次发货原因（首次发货为空） */
    private String reason;
    /** 操作管理员邮箱（查无用户时为空） */
    private String operatorEmail;
    /** 本次发信收件地址 */
    private String emailTo;
    /** 邮件发送结果：SENT/FAILED */
    private String emailStatus;
    /** 邮件失败原因（成功为空） */
    private String emailError;
    /** 发货时间（绝对时刻，ISO-8601 UTC） */
    private Instant shippedAt;
}
