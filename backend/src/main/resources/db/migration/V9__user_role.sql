-- 用户角色：管理员身份从配置项白名单（app.auth.admin-emails）改为落在用户行上
-- 存量与新注册用户一律 USER，不回填；提权只走手工 SQL：
--   UPDATE shop_user SET `role` = 'ADMIN' WHERE email = '管理员邮箱';
ALTER TABLE shop_user
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER'
        COMMENT '角色（USER=普通用户，ADMIN=管理员；仅由管理员直接改库维护，产品侧无写入口）'
        AFTER avatar_url;
