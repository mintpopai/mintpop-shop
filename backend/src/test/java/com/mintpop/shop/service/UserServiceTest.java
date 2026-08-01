package com.mintpop.shop.service;

import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.entity.UserIdentity;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.mapper.UserIdentityMapper;
import com.mintpop.shop.enumeration.UserRoleEnum;
import com.mintpop.shop.response.MeResponse;
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
    @InjectMocks
    private UserService userService;

    private UserIdentity identity(String sub, Long userId) {
        UserIdentity i = new UserIdentity();
        i.setSub(sub);
        i.setUserId(userId);
        return i;
    }

    private ShopUser user(Long id, String email) {
        return user(id, email, UserRoleEnum.USER);
    }

    private ShopUser user(Long id, String email, UserRoleEnum role) {
        ShopUser u = new ShopUser();
        u.setId(id);
        u.setEmail(email);
        u.setNickname("旧昵称");
        u.setRole(role);
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

        ShopUser result = userService.syncOnLogin("sub-1", "a@b.com", "小明", "https://img/x.png", "zh-CN");

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getEmail()).isEqualTo("a@b.com");
        assertThat(result.getNickname()).isEqualTo("小明");
        // 注册一律普通用户，提权只由管理员改库
        assertThat(result.getRole()).isEqualTo(UserRoleEnum.USER);
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

        ShopUser result = userService.syncOnLogin("sub-1", "a@b.com", "新名字", "https://img/new.png", "zh-CN");

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

        ShopUser result = userService.syncOnLogin("sub-1", "new@b.com", "小明", null, "zh-CN");

        assertThat(result.getEmail()).isEqualTo("new@b.com");
        ArgumentCaptor<ShopUser> captor = ArgumentCaptor.forClass(ShopUser.class);
        verify(shopUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@b.com");
        // 只刷新 email，昵称保持本地值
        assertThat(captor.getValue().getNickname()).isEqualTo("旧昵称");
    }

    @Test
    @DisplayName("getMe：返回用户档案，ADMIN 角色带管理员标志")
    void getMeReturnsProfile() {
        when(shopUserMapper.selectById(7L)).thenReturn(user(7L, "a@b.com", UserRoleEnum.ADMIN));

        MeResponse me = userService.getMe(7L);

        assertThat(me.getId()).isEqualTo(7L);
        assertThat(me.getEmail()).isEqualTo("a@b.com");
        assertThat(me.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("getMe：USER 角色不带管理员标志")
    void getMeForNormalUserIsNotAdmin() {
        when(shopUserMapper.selectById(8L)).thenReturn(user(8L, "b@b.com", UserRoleEnum.USER));

        assertThat(userService.getMe(8L).isAdmin()).isFalse();
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

    @Test
    @DisplayName("保存语言偏好：白名单值写库")
    void updatesLocale() {
        when(shopUserMapper.updateById(any(ShopUser.class))).thenReturn(1);

        userService.updateLocale(5L, "en-US");

        // 改为最小实体写回后：不再 selectById 读整行，只 set id + locale 两个字段就发起 updateById，
        // 断言这一点比原来（断言从整行读出的实体上 locale 被改）更强——它锁住了「不整实体回写」本身
        ArgumentCaptor<ShopUser> captor = ArgumentCaptor.forClass(ShopUser.class);
        verify(shopUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(5L);
        assertThat(captor.getValue().getLocale()).isEqualTo("en-US");
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getNickname()).isNull();
        verify(shopUserMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("保存语言偏好：用户不存在（受影响行数为 0）抛 310001，不再依赖 selectById 判存在性")
    void updateLocaleMissingUserThrows() {
        when(shopUserMapper.updateById(any(ShopUser.class))).thenReturn(0);

        assertThatThrownBy(() -> userService.updateLocale(99L, "en-US"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("保存语言偏好：非白名单值报参数校验失败，不写库")
    void rejectsUnknownLocale() {
        assertThatThrownBy(() -> userService.updateLocale(5L, "ja-JP"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PARAM_INVALID);

        verify(shopUserMapper, never()).updateById(any(ShopUser.class));
    }

    @Test
    @DisplayName("首次登录建号：按浏览器语言初始化偏好")
    void initialisesLocaleOnRegister() {
        when(userIdentityMapper.selectOne(any())).thenReturn(null);

        userService.syncOnLogin("sub-1", "a@b.com", "小明", "https://img/x.png", "en-US");

        ArgumentCaptor<ShopUser> captor = ArgumentCaptor.forClass(ShopUser.class);
        verify(shopUserMapper).insert(captor.capture());
        assertThat(captor.getValue().getLocale()).isEqualTo("en-US");
    }
}
