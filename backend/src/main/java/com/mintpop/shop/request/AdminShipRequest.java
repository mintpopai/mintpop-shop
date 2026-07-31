package com.mintpop.shop.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端发货请求。reason 首次发货可空，重新发货时服务层强制要求。
 */
@Data
public class AdminShipRequest {

    /** 发货内容文本 */
    @NotBlank(message = "{biz.validation.shipment-content-required}")
    @Size(max = 2000, message = "{biz.validation.shipment-content-max}")
    private String content;

    /** 本次发货原因（重新发货必填） */
    @Size(max = 255, message = "{biz.validation.shipment-reason-max}")
    private String reason;
}
