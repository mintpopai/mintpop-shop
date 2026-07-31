package com.mintpop.shop.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色：成员名与持久化字符串取值逐字一致（SCREAMING_SNAKE_CASE）。
 * 角色由管理员直接改库维护，产品侧无写入口；管理端界面固定中文，文案由前端映射，不落后端 i18n。
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {

    USER("USER"),
    ADMIN("ADMIN");

    /** 持久化到数据库的取值 */
    @EnumValue
    private final String value;
}
