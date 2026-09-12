package com.mintpop.shop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

    private StorageProperties full() {
        StorageProperties p = new StorageProperties();
        p.setAccountId("acc123");
        p.setAccessKeyId("ak");
        p.setSecretAccessKey("sk");
        p.setBucket("mintpop-shop-assets");
        p.setPublicBaseUrl("https://shop-assets.mintpop.ai");
        return p;
    }

    @Test
    @DisplayName("五项齐全才算已配置，缺任一项或为空白都视为未配置")
    void configuredOnlyWhenAllFivePresent() {
        assertThat(new StorageProperties().isConfigured()).isFalse();
        assertThat(full().isConfigured()).isTrue();

        StorageProperties missingBucket = full();
        missingBucket.setBucket("  ");
        assertThat(missingBucket.isConfigured()).isFalse();

        StorageProperties missingUrl = full();
        missingUrl.setPublicBaseUrl(null);
        assertThat(missingUrl.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("S3 端点由账户 ID 拼出")
    void endpointDerivedFromAccountId() {
        assertThat(full().endpoint()).isEqualTo("https://acc123.r2.cloudflarestorage.com");
    }
}
