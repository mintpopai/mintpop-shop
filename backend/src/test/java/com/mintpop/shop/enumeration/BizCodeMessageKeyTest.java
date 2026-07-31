package com.mintpop.shop.enumeration;

import com.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务码文案完整性：新增错误码必须同时补中英文案，漏一个这里就红。
 */
class BizCodeMessageKeyTest {

    private final MessageSource messageSource = TestMessages.create();

    @ParameterizedTest
    @EnumSource(BizCodeEnum.class)
    @DisplayName("每个业务码的文案键在中英 bundle 都能解析出非空文案")
    void messageKeyResolvesInBothLocales(BizCodeEnum code) {
        assertThat(messageSource.getMessage(code.getMessageKey(), null, Locale.SIMPLIFIED_CHINESE)).isNotBlank();
        assertThat(messageSource.getMessage(code.getMessageKey(), null, Locale.ENGLISH)).isNotBlank();
    }
}
