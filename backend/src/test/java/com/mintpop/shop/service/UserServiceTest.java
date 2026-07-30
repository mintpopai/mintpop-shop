package com.mintpop.shop.service;

import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.entity.UserIdentity;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.mapper.UserIdentityMapper;
import com.mintpop.shop.response.MeResponse;
import com.mintpop.shop.security.AdminChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private ShopUserMapper shopUserMapper;
    @Mock
    private UserIdentityMapper userIdentityMapper;
    @Mock
    private AdminChecker adminChecker;
    @InjectMocks
    private UserService userService;

    private UserIdentity identity(String sub, Long userId) {
        UserIdentity i = new UserIdentity();
        i.setSub(sub);
        i.setUserId(userId);
        return i;
    }

    private ShopUser user(Long id, String email) {
        ShopUser u = new ShopUser();
        u.setId(id);
        u.setEmail(email);
        u.setNickname("旧昵称");
        return u;
    }

    @Test
    @DisplayName("首次登录：建号 + 建映射，ID Token 资料作种子")
    void firstLoginCreatesUserAndMapping() {
        when(userIdentityMapper.selectOne(any())).thenReturn(null);
        when(shopUserMapper.insert(any(ShopUser.class))).thenAnswer(inv -> {
            inv.getArgument(0, ShopUser.class).setId(7L);
            return 1;
        });

        ShopUser result = userService.syncOnLogin("sub-1", "a@b.com", "小明", "https://img/x.png");

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.getNickname()).isEqualTo("小明");
        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityMapper).insert(captor.capture());
        assertThat(captor.getValue().getSub()).isEqualTo("sub-1");
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("再次登录：同 sub 不重复建号")
    void repeatLoginReusesUser() {
        when(userIdentityMapper.selectOne(any())).thenReturn(identity("sub-1", 7L));
        when(shopUserMapper.selectById(7L)).thenReturn(user(7L, "a@b.com"));

        ShopUser result = userService.syncOnLogin("sub-1", "a@b.com", "新名字", "https://img/new.png");

        assertThat(result.getId()).isEqualTo(7L);
        verify(shopUserMapper, never()).insert(any(ShopUser.class));
        // 昵称/头像是本产品资料，登录不回写覆盖
        verify(shopUserMapper, never()).updateById(any(ShopUser.class));
    }

    @Test
    @DisplayName("账号中心改了邮箱：认 sub、刷新本地 email 只读副本")
    void emailChangeRefreshesLocalCopy() {
        when(userIdentityMapper.selectOne(any())).thenReturn(identity("sub-1", 7L));
        when(shopUserMapper.selectById(7L)).thenReturn(user(7L, "old@b.com"));

        ShopUser result = userService.syncOnLogin("sub-1", "new@b.com", "小明", null);

        assertThat(result.getEmail()).isEqualTo("new@b.com");
        ArgumentCaptor<ShopUser> captor = ArgumentCaptor.forClass(ShopUser.class);
        verify(shopUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@b.com");
        // 只刷新 email，昵称保持本地值
        assertThat(captor.getValue().getNickname()).isEqualTo("旧昵称");
    }

    @Test
    @DisplayName("getMe：返回用户档案，管理员标志由 AdminChecker 裁决")
    void getMeReturnsProfile() {
        when(shopUserMapper.selectById(7L)).thenReturn(user(7L, "a@b.com"));
        when(adminChecker.isAdmin("a@b.com")).thenReturn(true);

        MeResponse me = userService.getMe(7L);

        assertThat(me.getId()).isEqualTo(7L);
        assertThat(me.getEmail()).isEqualTo("a@b.com");
        assertThat(me.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("getMe：用户不存在抛 310001")
    void getMeMissingUserThrows() {
        when(shopUserMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getMe(99L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.USER_NOT_FOUND);
    }
}
