package com.mintpop.shop.client;

import com.mintpop.shop.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2StorageClientTest {

    private S3Client s3Client;
    private StorageProperties properties;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        properties = new StorageProperties();
        properties.setBucket("mintpop-shop-assets");
        properties.setPublicBaseUrl("https://shop-assets.mintpop.ai");
    }

    @Test
    @DisplayName("写对象：桶、键、Content-Type 与一年期不可变缓存头都带上，返回公开 URL")
    void putsObjectWithHeadersAndReturnsPublicUrl() {
        R2StorageClient client = new R2StorageClient(s3Client, properties);

        String url = client.put("products/2026/09/abc.png", "image/png", new byte[]{1, 2, 3});

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo("mintpop-shop-assets");
        assertThat(req.key()).isEqualTo("products/2026/09/abc.png");
        assertThat(req.contentType()).isEqualTo("image/png");
        assertThat(req.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(url).isEqualTo("https://shop-assets.mintpop.ai/products/2026/09/abc.png");
    }

    @Test
    @DisplayName("public-base-url 带末尾斜杠时不会拼出双斜杠")
    void stripsTrailingSlashOfBaseUrl() {
        properties.setPublicBaseUrl("https://shop-assets.mintpop.ai/");
        R2StorageClient client = new R2StorageClient(s3Client, properties);

        assertThat(client.put("products/2026/09/abc.png", "image/png", new byte[]{1}))
                .isEqualTo("https://shop-assets.mintpop.ai/products/2026/09/abc.png");
    }

    @Test
    @DisplayName("SDK 异常原样上抛，由服务层翻译成业务码")
    void propagatesSdkException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("AccessDenied").statusCode(403).build());
        R2StorageClient client = new R2StorageClient(s3Client, properties);

        assertThatThrownBy(() -> client.put("k", "image/png", new byte[]{1}))
                .isInstanceOf(S3Exception.class);
    }
}
