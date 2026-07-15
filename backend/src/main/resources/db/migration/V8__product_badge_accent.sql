-- 首页改版：商品卡角标（双语，空=不显示）与主题色枚举
ALTER TABLE product
    ADD COLUMN badge_zh VARCHAR(32) NULL COMMENT '角标（中文），空=不显示' AFTER description_en,
    ADD COLUMN badge_en VARCHAR(32) NULL COMMENT '角标（英文），空串回退中文' AFTER badge_zh,
    ADD COLUMN accent VARCHAR(16) NOT NULL DEFAULT 'MINT' COMMENT '卡片主题色枚举：MINT/VIOLET/SKY/AMBER/ROSE' AFTER badge_en;

-- 给现有种子商品配角标与主题色，开箱即有设计图效果
UPDATE product SET badge_zh = '经典款', badge_en = 'Classic', accent = 'MINT' WHERE name_zh = '薄荷精灵盲盒';
UPDATE product SET badge_zh = '隐藏款 1/72', badge_en = 'Secret 1/72', accent = 'VIOLET' WHERE name_zh = '云朵萌宠盲盒';
UPDATE product SET badge_zh = '限定', badge_en = 'Limited', accent = 'SKY' WHERE name_zh = '星海航员盲盒';
UPDATE product SET accent = 'AMBER' WHERE name_zh = '复古街机盲盒';
UPDATE product SET badge_zh = '旗舰', badge_en = 'Flagship', accent = 'MINT' WHERE name_zh = '薄荷猫手办';
UPDATE product SET accent = 'SKY' WHERE name_zh = '气泡熊摆件';
UPDATE product SET badge_zh = '夜光', badge_en = 'Glow', accent = 'VIOLET' WHERE name_zh = '月光兔手办';
UPDATE product SET accent = 'MINT' WHERE name_zh = '薄荷帆布袋';
UPDATE product SET accent = 'AMBER' WHERE name_zh = '亚克力钥匙扣';
UPDATE product SET badge_zh = '热卖', badge_en = 'Hot', accent = 'ROSE' WHERE name_zh = '贴纸套装';
