package com.mintpop.shop.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    @DisplayName("剥离 script 标签及其内容")
    void stripsScriptTag() {
        String cleaned = sanitizer.sanitize("<p>正文</p><script>alert(1)</script>");

        assertThat(cleaned).isEqualTo("<p>正文</p>");
    }

    @Test
    @DisplayName("剥离事件处理属性")
    void stripsEventHandlerAttribute() {
        String cleaned = sanitizer.sanitize("<img src=\"https://cdn.example.com/a.png\" onerror=\"alert(1)\">");

        assertThat(cleaned).doesNotContain("onerror");
        assertThat(cleaned).contains("https://cdn.example.com/a.png");
    }

    @Test
    @DisplayName("剥离 javascript: 协议链接")
    void stripsJavascriptProtocolLink() {
        String cleaned = sanitizer.sanitize("<a href=\"javascript:alert(1)\">点我</a>");

        assertThat(cleaned).doesNotContain("javascript:");
        assertThat(cleaned).contains("点我");
    }

    @Test
    @DisplayName("保留常规富文本标签与结构")
    void keepsRichTextMarkup() {
        String html = "<h2>标题</h2><p><strong>粗</strong><em>斜</em></p><ul><li>项</li></ul>"
                + "<blockquote>引</blockquote><a href=\"https://example.com\">链接</a>";

        assertThat(sanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    @DisplayName("外链保留 target 与 rel，供前端新开标签页")
    void keepsLinkTargetAndRel() {
        String html = "<a href=\"https://example.com\" target=\"_blank\" rel=\"noopener\">链接</a>";

        assertThat(sanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    @DisplayName("空白输入归一为 null，库里不混存空串")
    void blankBecomesNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("   ")).isNull();
    }

    @Test
    @DisplayName("净化后只剩空标签的内容也归一为 null")
    void markupWithoutTextBecomesNull() {
        assertThat(sanitizer.sanitize("<script>alert(1)</script>")).isNull();
    }
}
