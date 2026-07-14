package com.mintpop.shop.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class I18nUtilTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("语言子标签为 en 判为英文（en/en-US 均命中），其余为中文")
    void isEnglishByLanguageSubtag() {
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(I18nUtil.isEnglish()).isTrue();

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(I18nUtil.isEnglish()).isTrue();

        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertThat(I18nUtil.isEnglish()).isFalse();

        LocaleContextHolder.setLocale(Locale.JAPAN);
        assertThat(I18nUtil.isEnglish()).isFalse();
    }

    @Test
    @DisplayName("pick：英文且英文值非空白取英文，否则回退中文")
    void pickFallsBackToChinese() {
        assertThat(I18nUtil.pick(true, "Mint Cat", "薄荷猫")).isEqualTo("Mint Cat");
        assertThat(I18nUtil.pick(true, "", "薄荷猫")).isEqualTo("薄荷猫");
        assertThat(I18nUtil.pick(true, null, "薄荷猫")).isEqualTo("薄荷猫");
        assertThat(I18nUtil.pick(false, "Mint Cat", "薄荷猫")).isEqualTo("薄荷猫");
    }
}
