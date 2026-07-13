package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一身份映射实体（表 user_identity）：sub 与 userid 一一对应，认人只走这张表。
 */
@Data
@TableName("user_identity")
public class UserIdentity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 账号中心 OIDC subject */
    private String sub;
    /** 内部用户ID（shop_user.id） */
    private Long userId;
    /** 建号时间（数据库默认值维护） */
    private LocalDateTime createdAt;
}
