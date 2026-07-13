package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    /** 商品名 */
    private String name;
    /** 商品描述 */
    private String description;
    /** 价格，单位分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;
    /** 是否上架 */
    private Boolean onSale;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
