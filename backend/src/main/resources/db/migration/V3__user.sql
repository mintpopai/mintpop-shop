-- 用户（业务档案，主键即产品内部 userid）
CREATE TABLE shop_user (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键（产品内部 userid，业务表一律引用它）',
    email      VARCHAR(255)    NOT NULL COMMENT '邮箱（账号中心只读副本，每次登录刷新，产品侧无修改入口）',
    nickname   VARCHAR(128)    NULL COMMENT '昵称（首次登录取 ID Token name 作种子，此后归本产品托管）',
    avatar_url VARCHAR(512)    NULL COMMENT '头像URL（首次登录取 ID Token picture 作种子，此后归本产品托管）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='用户（业务档案）';

-- 统一身份映射（sub 对应 userid，认人只走这张表）
CREATE TABLE user_identity (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    sub        VARCHAR(128)    NOT NULL COMMENT '账号中心 OIDC subject（全组织唯一的外部身份标识）',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '内部用户ID（shop_user.id）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建号时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub (sub),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='统一身份映射（sub 与 userid 一一对应）';

-- 订单 user_id 由预留转正（存量游客订单保持 NULL）
ALTER TABLE shop_order
    MODIFY COLUMN user_id BIGINT UNSIGNED NULL COMMENT '下单用户ID（shop_user.id；存量游客订单为空）';
