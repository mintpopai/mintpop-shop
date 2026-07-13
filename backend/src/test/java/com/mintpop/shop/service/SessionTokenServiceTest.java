package com.mintpop.shop.service;

import com.mintpop.shop.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";

    private AuthProperties props(Duration ttl) {
        AuthProperties p = new AuthProperties();
        p.setSessionSecret(SECRET);
        p.setSessionTtl(ttl);
        return p;
    }

    @Test
    @DisplayName("签发后可解析出同一 userid")
    void issueThenParse() {
        SessionTokenService service = new SessionTokenService(props(Duration.ofMinutes(5)));
        String token = service.issue(42L);
        assertThat(service.parse(token)).contains(42L);
    }

    @Test
    @DisplayName("篡改的 token 解析为空")
    void tamperedTokenRejected() {
        SessionTokenService service = new SessionTokenService(props(Duration.ofMinutes(5)));
        String token = service.issue(42L);
        assertThat(service.parse(token + "x")).isEmpty();
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("过期 token 解析为空")
    void expiredTokenRejected() {
        SessionTokenService service = new SessionTokenService(props(Duration.ofSeconds(-10)));
        String token = service.issue(42L);
        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("换密钥后旧 token 失效")
    void differentSecretRejected() {
        String token = new SessionTokenService(props(Duration.ofMinutes(5))).issue(42L);
        AuthProperties other = props(Duration.ofMinutes(5));
        other.setSessionSecret("another-secret-another-secret-another-secret");
        assertThat(new SessionTokenService(other).parse(token)).isEmpty();
    }

    @Test
    @DisplayName("密钥缺失或过短：启动即报清晰错误")
    void missingSecretFailsFast() {
        AuthProperties p = new AuthProperties();
        assertThatThrownBy(() -> new SessionTokenService(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session-secret");
    }
}
