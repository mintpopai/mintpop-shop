package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户响应体。
 */
@Data
@AllArgsConstructor
public class MeResponse {

    /** 内部用户ID */
    private Long id;
    /** 邮箱（账号中心只读副本） */
    private String email;
    /** 昵称 */
    private String nickname;
    /** 头像URL */
    private String avatarUrl;
    /** 是否管理员（前端据此显示管理入口；安全边界在后端拦截器） */
    private boolean admin;
}
