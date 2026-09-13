-- 商品库存：NULL=不限（不扣减、不售罄）；>=0 为当前可售数，下单预占扣减、取消/过期归还
ALTER TABLE product
    ADD COLUMN stock INT NULL COMMENT '库存数量：NULL=不限库存；>=0 为当前可售数（下单预占扣减，取消/过期归还，入账竞态补扣时可为负）' AFTER on_sale;

-- 订单对库存的占用状态，归还/成交按它做条件更新保证幂等
ALTER TABLE shop_order
    ADD COLUMN stock_hold VARCHAR(16) NOT NULL DEFAULT 'NONE'
        COMMENT '库存占用状态：NONE=未预占（下单时商品不限库存或存量订单） HELD=预占中 RELEASED=已归还 CONSUMED=已成交' AFTER quantity;
