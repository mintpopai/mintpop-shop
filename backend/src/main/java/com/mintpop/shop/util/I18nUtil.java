package com.mintpop.shop.util;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 请求语言判定与多语言列取值工具。
 * 语言规则全仓唯一：语言子标签为 en 视为英文，其余一律中文。
 */
public final class I18nUtil {

    private I18nUtil() {
    }

    /** 当前请求是否英文（en/en-US/en-GB 等） */
    public static boolean isEnglish() {
        return "en".equals(LocaleContextHolder.getLocale().getLanguage());
    }

    /** 按语言取双语列：英文且英文列非空白取英文，否则回退中文列（防漏翻出现空白） */
    public static String pick(boolean english, String en, String zh) {
        return english && en != null && !en.isBlank() ? en : zh;
    }
}
