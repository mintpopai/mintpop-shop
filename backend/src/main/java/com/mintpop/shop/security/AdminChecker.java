package com.mintpop.shop.security;

import com.mintpop.shop.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 管理员判定：邮箱是否命中配置白名单（app.auth.admin-emails，忽略大小写）。
 * 判定逻辑全仓唯一，拦截器与 /api/me 共用，避免两处口径漂移。
 */
@Component
@RequiredArgsConstructor
public class AdminChecker {

    private final AuthProperties authProperties;

    /** email 为空或白名单为空一律非管理员 */
    public boolean isAdmin(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return authProperties.getAdminEmails().stream().anyMatch(email::equalsIgnoreCase);
    }
}
