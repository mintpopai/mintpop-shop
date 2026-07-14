-- i18n：商品/分组改双语列（中文为权威列，英文空串回退中文），并补齐种子数据英文翻译。
-- V2 种子未显式指定商品 id，UPDATE 按中文名匹配。

ALTER TABLE product_group
    CHANGE COLUMN name name_zh VARCHAR(64) NOT NULL COMMENT '分组名（中文）',
    ADD COLUMN name_en VARCHAR(64) NOT NULL DEFAULT '' COMMENT '分组名（英文），空串回退中文' AFTER name_zh;

ALTER TABLE product
    CHANGE COLUMN name name_zh VARCHAR(128) NOT NULL COMMENT '商品名（中文）',
    CHANGE COLUMN description description_zh VARCHAR(512) NULL COMMENT '商品描述（中文）',
    ADD COLUMN name_en VARCHAR(128) NOT NULL DEFAULT '' COMMENT '商品名（英文），空串回退中文' AFTER name_zh,
    ADD COLUMN description_en VARCHAR(512) NULL COMMENT '商品描述（英文），空回退中文' AFTER description_zh;

UPDATE product_group SET name_en = 'Blind Boxes' WHERE name_zh = '盲盒系列';
UPDATE product_group SET name_en = 'Figures & Decor' WHERE name_zh = '手办摆件';
UPDATE product_group SET name_en = 'Accessories' WHERE name_zh = '周边小物';

UPDATE product SET name_en = 'Mint Sprite Blind Box',
                   description_en = 'Classic blind box with 12 random designs'
    WHERE name_zh = '薄荷精灵盲盒';
UPDATE product SET name_en = 'Cloud Pets Blind Box',
                   description_en = 'Fluffy cloud series with a 1/72 chance of a secret figure'
    WHERE name_zh = '云朵萌宠盲盒';
UPDATE product SET name_en = 'Starfield Voyager Blind Box',
                   description_en = 'Space-themed limited series'
    WHERE name_zh = '星海航员盲盒';
UPDATE product SET name_en = 'Retro Arcade Blind Box',
                   description_en = 'Pixel-art nostalgia series (off-sale sample)'
    WHERE name_zh = '复古街机盲盒';
UPDATE product SET name_en = 'Mint Cat Figure',
                   description_en = '18cm classic mint cat with display stand'
    WHERE name_zh = '薄荷猫手办';
UPDATE product SET name_en = 'Bubble Bear Ornament',
                   description_en = 'Translucent bubble texture, a perfect desk accent'
    WHERE name_zh = '气泡熊摆件';
UPDATE product SET name_en = 'Moonlight Rabbit Figure',
                   description_en = 'Glow-in-the-dark material, softly shines at night'
    WHERE name_zh = '月光兔手办';
UPDATE product SET name_en = 'Mint Canvas Tote',
                   description_en = 'Heavy-duty canvas with MintPop wordmark print'
    WHERE name_zh = '薄荷帆布袋';
UPDATE product SET name_en = 'Acrylic Keychain',
                   description_en = 'Random character, double-sided print'
    WHERE name_zh = '亚克力钥匙扣';
UPDATE product SET name_en = 'Sticker Pack',
                   description_en = '30 waterproof stickers for laptops and bottles'
    WHERE name_zh = '贴纸套装';
