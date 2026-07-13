package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（表 shop_user，业务档案；主键即产品内部 userid）。
 */
@Data
@TableName("shop_user")
public class ShopUser {

    /** 主键（产品内部 userid） */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 邮箱（账号中心只读副本，每次登录刷新） */
    private String email;
    /** 昵称（首次登录种子，此后本产品托管） */
    private String nickname;
    /** 头像URL（首次登录种子，此后本产品托管） */
    private String avatarUrl;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
