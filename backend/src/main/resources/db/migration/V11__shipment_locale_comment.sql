-- V10 已把 shop_user.locale 的注释写成「按请求语言兜底」，但后续修复已把兜底逻辑
-- 改成配置项 app.mail.default-locale（默认 zh-CN）——因为「请求语言」指的是管理员
-- 发起发货请求所用的语言，与买家本人的语言偏好无关，按它兜底会让偏好未写入的
-- 英文买家收到中文邮件。V10 脚本可能已在开发库执行过，直接改会变更 Flyway
-- checksum 导致启动校验失败，故只能新起一版迁移来修正注释；类型与可空性
-- 保持与 V10 完全一致（VARCHAR(16) NULL），只更新 COMMENT 文本。
ALTER TABLE shop_user
    MODIFY COLUMN locale VARCHAR(16) NULL COMMENT '语言偏好（BCP47：zh-CN/en-US）；空表示未设置，发信时回退配置项 app.mail.default-locale';
