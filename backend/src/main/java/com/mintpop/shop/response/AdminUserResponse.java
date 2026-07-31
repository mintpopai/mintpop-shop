package com.mintpop.shop.response;

import com.mintpop.shop.enumeration.UserRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 管理端用户列表项。
 */
@Data
@AllArgsConstructor
public class AdminUserResponse {

    /** 用户ID */
    private Long id;
    /** 邮箱 */
    private String email;
    /** 昵称 */
    private String nickname;
    /** 头像URL */
    private String avatarUrl;
    /** 角色（USER/ADMIN；只读展示，改角色只能直接改库） */
    private UserRoleEnum role;
    /** 该用户订单数 */
    private Long orderCount;
    /** 注册时间（ISO-8601 UTC 带 Z） */
    private Instant createdAt;
}
