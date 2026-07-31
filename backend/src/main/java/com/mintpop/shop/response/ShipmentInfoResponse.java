package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 用户可见的发货信息：只给最新一条，历史仅管理端可见。
 */
@Data
@AllArgsConstructor
public class ShipmentInfoResponse {

    /** 发货内容文本（前端按 pre-wrap 原样展示） */
    private String content;
    /** 发货时间（绝对时刻，ISO-8601 UTC） */
    private Instant shippedAt;
}
