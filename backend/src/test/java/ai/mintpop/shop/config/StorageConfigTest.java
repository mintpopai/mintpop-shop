package ai.mintpop.shop.config;

import ai.mintpop.shop.client.R2StorageClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StorageConfig 的条件装配：五项都非空白才建 bean，判据是 StorageProperties.isConfigured()。
 * S3Client.builder().build() 在测试里不联网，构造 bean 是安全的。
 */
class StorageConfigTest {

    /** 让 StorageProperties 可被 Binder 绑定，等价于生产环境靠 @ConfigurationPropertiesScan 注册 */
    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, StorageConfig.class);

    @Test
    @DisplayName("五项齐全：装配 S3Client 与 R2StorageClient")
    void allPropertiesPresent_beansConfigured() {
        contextRunner
                .withPropertyValues(
                        "storage.r2.account-id=acc",
                        "storage.r2.access-key-id=key",
                        "storage.r2.secret-access-key=secret",
                        "storage.r2.bucket=bucket",
                        "storage.r2.public-base-url=https://assets.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(R2StorageClient.class);
                });
    }

    @Test
    @DisplayName("缺 bucket：两个 bean 都不装配，容器仍正常启动")
    void missingBucket_beansAbsentButContextStarts() {
        contextRunner
                .withPropertyValues(
                        "storage.r2.account-id=acc",
                        "storage.r2.access-key-id=key",
                        "storage.r2.secret-access-key=secret",
                        "storage.r2.public-base-url=https://assets.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context).doesNotHaveBean(R2StorageClient.class);
                });
    }

    @Test
    @DisplayName("五项键都在但 access-key-id 为空串：不装配，且不会因空凭据崩溃")
    void blankAccessKeyId_beansAbsentButContextStarts() {
        contextRunner
                .withPropertyValues(
                        "storage.r2.account-id=acc",
                        "storage.r2.access-key-id=",
                        "storage.r2.secret-access-key=secret",
                        "storage.r2.bucket=bucket",
                        "storage.r2.public-base-url=https://assets.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context).doesNotHaveBean(R2StorageClient.class);
                });
    }
}
