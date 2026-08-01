package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.entity.UserIdentity;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.UserRoleEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.mapper.UserIdentityMapper;
import com.mintpop.shop.response.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * 用户服务：sub → userid 的边界解析（登录同步）与档案查询。
 * 认人只认 sub；email 是账号中心的只读副本，昵称/头像归本产品托管。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** 支持的语言偏好（BCP47），与前端 AppLocale 一一对应 */
    public static final Set<String> SUPPORTED_LOCALES = Set.of("zh-CN", "en-US");

    /** 昵称长度上限（去首尾空格后计），远小于列宽 VARCHAR(128)，纯产品约束 */
    public static final int NICKNAME_MAX_LENGTH = 30;

    private final ShopUserMapper shopUserMapper;
    private final UserIdentityMapper userIdentityMapper;

    /**
     * 登录同步：sub 命中映射则复用用户并刷新 email 副本；未命中视为首次登录（注册），
     * 用 ID Token 资料作种子建号 + 建映射（同一事务）。
     */
    @Transactional
    public ShopUser syncOnLogin(String sub, String email, String nickname, String avatarUrl, String locale) {
        UserIdentity identity = userIdentityMapper.selectOne(
                new LambdaQueryWrapper<UserIdentity>().eq(UserIdentity::getSub, sub));
        if (identity == null) {
            ShopUser user = new ShopUser();
            user.setEmail(email);
            user.setNickname(nickname);
            user.setAvatarUrl(avatarUrl);
            // 首次登录用浏览器语言作种子，此后由用户在站内切换语言时改写
            user.setLocale(locale);
            // 注册一律普通用户；提权只由管理员直接改库
            user.setRole(UserRoleEnum.USER);
            shopUserMapper.insert(user);

            UserIdentity mapping = new UserIdentity();
            mapping.setSub(sub);
            mapping.setUserId(user.getId());
            userIdentityMapper.insert(mapping);
            return user;
        }

        ShopUser user = shopUserMapper.selectById(identity.getUserId());
        // 只刷新 email（账号中心是唯一写入方）；昵称/头像登录不回写
        if (email != null && !Objects.equals(email, user.getEmail())) {
            user.setEmail(email);
            shopUserMapper.updateById(user);
        }
        return user;
    }

    /** 查询当前用户档案 */
    public MeResponse getMe(Long userId) {
        ShopUser user = shopUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizCodeEnum.USER_NOT_FOUND);
        }
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getAvatarUrl(),
                user.getRole() == UserRoleEnum.ADMIN, user.getLocale());
    }

    /**
     * 保存语言偏好：只接受白名单取值，越界按参数校验失败处理。
     * 用最小实体（只 set id + locale）写回，不先 selectById 再整实体回写：
     * README 允许管理员用裸 SQL 提权（UPDATE shop_user SET role=... WHERE email=...），
     * 若这里查询与写回之间恰好夹了一次提权，整实体写回会把它静默覆盖回旧角色；
     * 最小实体从根上避免这个 lost update，顺带省掉一次 selectById 及其自带的 TOCTOU。
     */
    public void updateLocale(Long userId, String locale) {
        if (locale == null || !SUPPORTED_LOCALES.contains(locale)) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }
        ShopUser patch = new ShopUser();
        patch.setId(userId);
        patch.setLocale(locale);
        if (shopUserMapper.updateById(patch) == 0) {
            throw new BizException(BizCodeEnum.USER_NOT_FOUND);
        }
    }

    /**
     * 保存个人档案（设置页「保存」）：昵称去首尾空格后与语言一次写回，避免两个接口出现「昵称成了、语言没成」的半截状态。
     * 请求层已做非空/长度校验，这里按同一规则再判一次（服务不假设只有 controller 会调），
     * 且仍走最小实体写回（只 set id + nickname + locale），理由同 updateLocale。
     */
    public void updateProfile(Long userId, String nickname, String locale) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty() || trimmed.length() > NICKNAME_MAX_LENGTH
                || locale == null || !SUPPORTED_LOCALES.contains(locale)) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }
        ShopUser patch = new ShopUser();
        patch.setId(userId);
        patch.setNickname(trimmed);
        patch.setLocale(locale);
        if (shopUserMapper.updateById(patch) == 0) {
            throw new BizException(BizCodeEnum.USER_NOT_FOUND);
        }
    }
}
