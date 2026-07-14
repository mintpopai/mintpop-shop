package com.mintpop.shop.support;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 测试用真实消息源：直接读 main resources 的 messages bundle，保证断言与线上文案一致。
 * 关闭系统语言回退，行为与 application.yml 的 fallback-to-system-locale=false 对齐。
 */
public final class TestMessages {

    private TestMessages() {
    }

    public static MessageSource create() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        return ms;
    }
}
