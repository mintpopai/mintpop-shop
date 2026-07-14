-- 支付接入：订单表加支付字段，状态值对齐品牌统一状态机（PENDING_PAYMENT → PENDING）
ALTER TABLE shop_order
    ADD COLUMN payment_provider VARCHAR(16) NULL COMMENT '支付处理方：stripe（未发起支付时为空）' AFTER status,
    ADD COLUMN payment_trade_no VARCHAR(64) NULL COMMENT '网关交易号（Stripe PaymentIntent ID）' AFTER payment_provider,
    ADD COLUMN paid_at DATETIME NULL COMMENT '支付完成时间' AFTER payment_trade_no,
    MODIFY COLUMN status VARCHAR(32) NOT NULL COMMENT '订单状态：PENDING=待支付 PAID=已支付 COMPLETED=已完成 CANCELLED=已取消 EXPIRED=已过期 FAILED=支付失败';

UPDATE shop_order SET status = 'PENDING' WHERE status = 'PENDING_PAYMENT';
