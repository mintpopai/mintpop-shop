package com.mintpop.shop.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开放重定向防护：合法站内路径判定 + 写入/读取一致性。
 */
class PostLoginRedirectCookieTest {

    private final PostLoginRedirectCookie cookie = new PostLoginRedirectCookie();

    @ParameterizedTest(name = "非法路径应被拒绝：{0}")
    @DisplayName("恶意/非法输入一律判定非法")
    @ValueSource(strings = {
            "//evil.com",
            "/\\evil.com",
            "https://evil.com",
            "http:/\\/\\evil.com",
            "orders/1",
    })
    void rejectsMaliciousOrInvalidPaths(String path) {
        assertThat(cookie.isValidPath(path)).isFalse();
    }

    @Test
    @DisplayName("含回车换行的值判定非法（防响应头注入）")
    void rejectsControlCharacters() {
        assertThat(cookie.isValidPath("/orders/1\r\nSet-Cookie: evil=1")).isFalse();
    }

    @Test
    @DisplayName("超长路径判定非法")
    void rejectsTooLongPath() {
        String tooLong = "/" + "a".repeat(600);
        assertThat(cookie.isValidPath(tooLong)).isFalse();
    }

    @Test
    @DisplayName("空串判定非法")
    void rejectsBlank() {
        assertThat(cookie.isValidPath("")).isFalse();
        assertThat(cookie.isValidPath("   ")).isFalse();
        assertThat(cookie.isValidPath(null)).isFalse();
    }

    @ParameterizedTest(name = "合法路径应被接受：{0}")
    @DisplayName("合法站内路径判定通过")
    @ValueSource(strings = {
            "/orders/mintpopshop_x",
            "/orders/x?tab=a",
    })
    void acceptsValidPaths(String path) {
        assertThat(cookie.isValidPath(path)).isTrue();
    }

    @Test
    @DisplayName("write 后可从 Cookie 原样 readValid 回同一路径")
    void writeThenReadValidRoundTrip() {
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        cookie.write(new MockHttpServletRequest(), writeResponse, "/orders/x?tab=a");

        MockHttpServletRequest readRequest = new MockHttpServletRequest();
        readRequest.setCookies(new Cookie(PostLoginRedirectCookie.COOKIE_NAME, cookieValue(writeResponse)));

        assertThat(cookie.readValid(readRequest)).isEqualTo("/orders/x?tab=a");
    }

    @Test
    @DisplayName("Cookie 值被篡改为非法路径时，readValid 返回 null（读取时二次校验）")
    void readValidRejectsTamperedCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String tampered = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("//evil.com".getBytes());
        request.setCookies(new Cookie(PostLoginRedirectCookie.COOKIE_NAME, tampered));

        assertThat(cookie.readValid(request)).isNull();
    }

    @Test
    @DisplayName("Cookie 内容损坏（非法 Base64）时 readValid 返回 null，不抛异常")
    void readValidReturnsNullOnCorruptedCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(PostLoginRedirectCookie.COOKIE_NAME, "not-base64-!!!"));

        assertThat(cookie.readValid(request)).isNull();
    }

    @Test
    @DisplayName("无 Cookie 时 readValid 返回 null")
    void readValidReturnsNullWithoutCookie() {
        assertThat(cookie.readValid(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("expire 写出 Max-Age=0 的过期 Cookie")
    void expireClearsCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookie.expire(new MockHttpServletRequest(), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(PostLoginRedirectCookie.COOKIE_NAME + "=")
                .contains("Max-Age=0");
    }

    /** 从响应的 Set-Cookie 头取 Cookie 值 */
    private String cookieValue(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull().startsWith(PostLoginRedirectCookie.COOKIE_NAME + "=");
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }
}
