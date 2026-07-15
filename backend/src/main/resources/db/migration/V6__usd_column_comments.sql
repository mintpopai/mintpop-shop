-- 结算币种统一为美元（USD），金额列注释从“分”更正为“美分”（仅改注释，列定义不变）
ALTER TABLE product
    MODIFY COLUMN price_cents BIGINT NOT NULL COMMENT '价格，单位美分';

ALTER TABLE shop_order
    MODIFY COLUMN amount_cents BIGINT NOT NULL COMMENT '订单金额，单位美分';
