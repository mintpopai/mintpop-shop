-- 商品详情页：管理端配置的富文本详情（双语，空回退中文；均空时详情页回退短描述）
ALTER TABLE product
    ADD COLUMN detail_zh MEDIUMTEXT NULL COMMENT '商品详情富文本HTML（中文），入库前已白名单净化' AFTER description_en,
    ADD COLUMN detail_en MEDIUMTEXT NULL COMMENT '商品详情富文本HTML（英文），空回退中文' AFTER detail_zh;
