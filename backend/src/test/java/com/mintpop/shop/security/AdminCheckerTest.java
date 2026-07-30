package com.mintpop.shop.security;

import com.mintpop.shop.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCheckerTest {

    private AdminChecker checker(List<String> adminEmails) {
        AuthProperties props = new AuthProperties();
        props.setAdminEmails(adminEmails);
        return new AdminChecker(props);
    }

    @Test
    @DisplayName("命中白名单即管理员，忽略大小写")
    void matchesIgnoringCase() {
        AdminChecker checker = checker(List.of("Boss@MintPop.ai"));

        assertThat(checker.isAdmin("boss@mintpop.ai")).isTrue();
        assertThat(checker.isAdmin("BOSS@MINTPOP.AI")).isTrue();
        assertThat(checker.isAdmin("other@mintpop.ai")).isFalse();
    }

    @Test
    @DisplayName("白名单为空或邮箱为空一律非管理员")
    void emptyInputsAreNotAdmin() {
        assertThat(checker(List.of()).isAdmin("boss@mintpop.ai")).isFalse();
        assertThat(checker(List.of("boss@mintpop.ai")).isAdmin(null)).isFalse();
        assertThat(checker(List.of("boss@mintpop.ai")).isAdmin("  ")).isFalse();
    }
}
