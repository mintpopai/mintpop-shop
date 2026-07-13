-- 商品分组
CREATE TABLE product_group (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64)     NOT NULL COMMENT '分组名',
    sort_order INT             NOT NULL DEFAULT 0 COMMENT '排序号，小的在前',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商品分组';

-- 商品
CREATE TABLE product (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    group_id    BIGINT UNSIGNED NOT NULL COMMENT '所属分组ID（product_group.id）',
    name        VARCHAR(128)    NOT NULL COMMENT '商品名',
    description VARCHAR(512)    NULL COMMENT '商品描述',
    price_cents BIGINT          NOT NULL COMMENT '价格，单位分',
    image_url   VARCHAR(512)    NULL COMMENT '商品图URL，可空（空时前端渲染占位图）',
    on_sale     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否上架：1=上架 0=下架',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_group_id (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商品';

-- 订单（order 是保留字，表名加 shop_ 前缀）
CREATE TABLE shop_order (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)     NOT NULL COMMENT '对外订单号，唯一',
    product_id   BIGINT UNSIGNED NOT NULL COMMENT '商品ID（product.id）',
    quantity     INT             NOT NULL COMMENT '购买数量',
    amount_cents BIGINT          NOT NULL COMMENT '订单金额，单位分',
    status       VARCHAR(32)     NOT NULL COMMENT '订单状态：PENDING_PAYMENT=待支付（后续扩展 PAID/CANCELLED 等）',
    user_id      BIGINT UNSIGNED NULL COMMENT '下单用户ID，预留（接入注册后填写）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单';
