package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    /** 分组名（中文） */
    private String nameZh;
    /** 分组名（英文），空串回退中文 */
    private String nameEn;
    /** 排序号，小的在前 */
    private Integer sortOrder;
    /** 创建时间（数据库默认值维护）；updateStrategy=NEVER 防止整实体写回时把该列带进 UPDATE 的 SET，压制数据库侧的默认值/触发逻辑 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    /** 更新时间（数据库 ON UPDATE CURRENT_TIMESTAMP 维护）；updateStrategy=NEVER 防止整实体写回时显式回填旧值，导致 ON UPDATE 不触发、时间戳停滞 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
