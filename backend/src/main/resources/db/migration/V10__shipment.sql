-- 发货记录：一单可多次发货，全部留痕；用户只看最新一条，管理员看全部
CREATE TABLE order_shipment (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id         BIGINT UNSIGNED NOT NULL COMMENT '订单ID（shop_order.id）',
    content          TEXT            NOT NULL COMMENT '发货内容文本（原样展示给用户，保留换行）',
    reason           VARCHAR(255)    NULL COMMENT '本次发货原因：首次发货为空，重新发货时必填',
    operator_user_id BIGINT UNSIGNED NOT NULL COMMENT '操作管理员用户ID（shop_user.id）',
    email_to         VARCHAR(255)    NOT NULL COMMENT '本次发信收件地址（留痕，用户日后改邮箱不影响历史）',
    email_status     VARCHAR(16)     NOT NULL COMMENT '邮件发送结果：SENT=已发送 FAILED=发送失败',
    email_error      VARCHAR(512)    NULL COMMENT '邮件发送失败原因（成功为空）',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发货时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单发货记录';

ALTER TABLE shop_user
    ADD COLUMN locale VARCHAR(16) NULL COMMENT '语言偏好（BCP47：zh-CN/en-US）；空表示未设置，按请求语言兜底';
