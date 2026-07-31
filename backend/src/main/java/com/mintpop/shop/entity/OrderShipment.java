package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mintpop.shop.enumeration.ShipmentEmailStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单发货记录实体（表 order_shipment）。一单可多次发货，本表全量留痕。
 */
@Data
@TableName("order_shipment")
public class OrderShipment {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 订单ID（shop_order.id） */
    private Long orderId;
    /** 发货内容文本 */
    private String content;
    /** 本次发货原因：首次为空，重新发货时必填 */
    private String reason;
    /** 操作管理员用户ID */
    private Long operatorUserId;
    /** 本次发信收件地址（留痕） */
    private String emailTo;
    /** 邮件发送结果 */
    private ShipmentEmailStatusEnum emailStatus;
    /** 邮件发送失败原因（成功为空） */
    private String emailError;
    /** 发货时间 */
    private LocalDateTime createdAt;
}
