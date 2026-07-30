package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminUserResponse;
import com.mintpop.shop.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端用户查询服务：注册用户分页列表（附订单数）。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** 单页上限，防误传大页拖库 */
    private static final long MAX_PAGE_SIZE = 100;

    private final ShopUserMapper shopUserMapper;
    private final ShopOrderMapper shopOrderMapper;

    /** 分页查询用户（新注册在前），附各用户订单数 */
    public PageResponse<AdminUserResponse> pageUsers(long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<ShopUser> result = shopUserMapper.selectPage(Page.of(safePage, safeSize),
                new LambdaQueryWrapper<ShopUser>().orderByDesc(ShopUser::getId));
        List<ShopUser> users = result.getRecords();

        Map<Long, Long> orderCountByUser = users.isEmpty() ? Map.of()
                : shopOrderMapper.selectMaps(new QueryWrapper<ShopOrder>()
                                .select("user_id AS userId", "COUNT(*) AS cnt")
                                .in("user_id", users.stream().map(ShopUser::getId).toList())
                                .groupBy("user_id")).stream()
                        .collect(Collectors.toMap(
                                row -> ((Number) row.get("userId")).longValue(),
                                row -> ((Number) row.get("cnt")).longValue()));

        List<AdminUserResponse> records = users.stream()
                .map(u -> new AdminUserResponse(u.getId(), u.getEmail(), u.getNickname(), u.getAvatarUrl(),
                        orderCountByUser.getOrDefault(u.getId(), 0L),
                        u.getCreatedAt().toInstant(ZoneOffset.UTC)))
                .toList();
        return new PageResponse<>(records, result.getTotal(), safePage, safeSize);
    }
}
