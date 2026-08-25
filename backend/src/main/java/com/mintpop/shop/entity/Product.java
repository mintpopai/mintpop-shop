package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品实体（表 product）。
 */
@Data
@TableName("product")
public class Product {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属分组ID */
    private Long groupId;
    /** 商品名（中文） */
    private String nameZh;
    /** 商品名（英文），空串回退中文 */
    private String nameEn;
    /** 商品描述（中文） */
    private String descriptionZh;
    /** 商品描述（英文），空回退中文 */
    private String descriptionEn;
    /** 商品详情富文本HTML（中文），空=详情页回退短描述 */
    private String detailZh;
    /** 商品详情富文本HTML（英文），空回退中文 */
    private String detailEn;
    /** 角标（中文），空=不显示 */
    private String badgeZh;
    /** 角标（英文），空串回退中文 */
    private String badgeEn;
    /** 卡片主题色枚举：MINT/VIOLET/SKY/AMBER/ROSE */
    private String accent;
    /** 价格，单位美分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;
    /** 是否上架 */
    private Boolean onSale;
    /** 创建时间（数据库默认值维护）；updateStrategy=NEVER 防止整实体写回时把该列带进 UPDATE 的 SET，压制数据库侧的默认值/触发逻辑 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    /** 更新时间（数据库 ON UPDATE CURRENT_TIMESTAMP 维护）；updateStrategy=NEVER 防止整实体写回时显式回填旧值，导致 ON UPDATE 不触发、时间戳停滞 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
