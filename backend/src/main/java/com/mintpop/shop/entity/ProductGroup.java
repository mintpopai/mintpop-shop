package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分组实体（表 product_group）。
 */
@Data
@TableName("product_group")
public class ProductGroup {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 分组名 */
    private String name;
    /** 排序号，小的在前 */
    private Integer sortOrder;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
