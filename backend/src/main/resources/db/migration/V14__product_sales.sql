-- 商品销量分两部分：展示销量（管理员手填的基数）+ 实际销量（订单入账时按购买数量累加）；前台只看两者之和
ALTER TABLE product
    ADD COLUMN display_sales INT NOT NULL DEFAULT 0 COMMENT '展示销量：管理员手填的销量基数，不随订单变化；前台展示 = display_sales + sold_count' AFTER stock,
    ADD COLUMN sold_count    INT NOT NULL DEFAULT 0 COMMENT '实际销量：订单首次入账（置 PAID）时累加购买数量，只增不减（无退款流程）' AFTER display_sales;

-- 回填：上线前已付款/已完成的订单也算进实际销量，否则存量商品的销量从 0 起跳
UPDATE product p
SET p.sold_count = (
    SELECT COALESCE(SUM(o.quantity), 0)
    FROM shop_order o
    WHERE o.product_id = p.id
      AND o.status IN ('PAID', 'COMPLETED')
);
