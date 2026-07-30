package com.mintpop.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminUserResponse;
import com.mintpop.shop.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private ShopUserMapper shopUserMapper;
    @Mock
    private ShopOrderMapper shopOrderMapper;
    @InjectMocks
    private AdminUserService adminUserService;

    private ShopUser user(long id, String email) {
        ShopUser u = new ShopUser();
        u.setId(id);
        u.setEmail(email);
        u.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        return u;
    }

    @Test
    @DisplayName("分页附各用户订单数，无订单用户计 0")
    void pageAttachesOrderCounts() {
        Page<ShopUser> page = new Page<>(1, 20);
        page.setRecords(List.of(user(7L, "a@b.com"), user(8L, "c@d.com")));
        page.setTotal(2);
        when(shopUserMapper.selectPage(any(), any())).thenReturn(page);
        when(shopOrderMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("userId", 7L, "cnt", 5L)));

        PageResponse<AdminUserResponse> result = adminUserService.pageUsers(1, 20);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getOrderCount()).isEqualTo(5L);
        assertThat(result.getRecords().get(1).getOrderCount()).isEqualTo(0L);
        assertThat(result.getRecords().get(0).getCreatedAt().toString()).isEqualTo("2026-07-01T00:00:00Z");
    }

    @Test
    @DisplayName("当页无用户时不再发订单聚合查询")
    void emptyPageSkipsOrderAggregation() {
        Page<ShopUser> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(shopUserMapper.selectPage(any(), any())).thenReturn(page);

        PageResponse<AdminUserResponse> result = adminUserService.pageUsers(1, 20);

        assertThat(result.getRecords()).isEmpty();
        verify(shopOrderMapper, never()).selectMaps(any());
    }
}
