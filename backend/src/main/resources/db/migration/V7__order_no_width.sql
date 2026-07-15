-- 订单号前缀由 MP 改为 mintpopshop_（多业务共用 Stripe 账户，前缀标识业务线），
-- 新格式 32 位恰好顶满原 VARCHAR(32) 上限，放宽到 64 留余量；存量 MP 前缀订单号不迁移、继续有效
ALTER TABLE shop_order
    MODIFY COLUMN order_no VARCHAR(64) NOT NULL COMMENT '对外订单号，唯一（mintpopshop_ + 时间戳 + 6 位随机）';
