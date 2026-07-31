package com.mintpop.shop.security;

import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.UserRoleEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ShopUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端拦截器（/api/admin/**）：登录已由安全链保证，这里只裁决管理员身份。
 * 每请求按当前用户的 role 现查现判（管理端流量低），改库提权即时生效、无需重启。
 * 非管理员抛业务异常，由全局异常处理器转统一 ApiResponse。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final ShopUserMapper shopUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            // /api/** 已要求登录，走到这里拿不到用户说明安全配置有误
            throw new IllegalStateException("管理端接口未取到登录用户，请检查安全配置");
        }
        ShopUser user = shopUserMapper.selectById(userId);
        if (user == null || user.getRole() != UserRoleEnum.ADMIN) {
            throw new BizException(BizCodeEnum.PERMISSION_DENIED);
        }
        return true;
    }
}
