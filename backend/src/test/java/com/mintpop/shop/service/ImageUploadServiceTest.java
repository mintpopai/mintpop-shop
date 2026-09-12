package com.mintpop.shop.service;

import com.mintpop.shop.client.R2StorageClient;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.response.ImageUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageUploadServiceTest {

    private static final byte[] PNG_HEADER =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};

    private R2StorageClient storage;
    private ImageUploadService service;

    @BeforeEach
    void setUp() {
        storage = mock(R2StorageClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T10:00:00Z"), ZoneOffset.UTC);
        service = new ImageUploadService(Optional.of(storage), clock);
    }

    private static MockMultipartFile png() {
        // 浏览器传的文件名与 Content-Type 都故意写错：判型只信魔数
        return new MockMultipartFile("file", "cat.txt", "text/plain", PNG_HEADER);
    }

    @Test
    @DisplayName("成功：对象键为 products/年/月/uuid.真实扩展名，Content-Type 按魔数，返回公开 URL")
    void uploadsWithGeneratedKey() {
        when(storage.put(anyString(), eq("image/png"), any()))
                .thenAnswer(inv -> "https://shop-assets.mintpop.ai/" + inv.getArgument(0));

        ImageUploadResponse resp = service.upload(png());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq("image/png"), eq(PNG_HEADER));
        assertThat(key.getValue()).matches("products/2026/09/[0-9a-f-]{36}\\.png");
        assertThat(resp.getUrl()).isEqualTo("https://shop-assets.mintpop.ai/" + key.getValue());
    }

    @Test
    @DisplayName("存储未装配：抛图片存储未配置，不碰任何东西")
    void notConfigured() {
        ImageUploadService unconfigured = new ImageUploadService(Optional.empty(), Clock.systemUTC());

        assertThatThrownBy(() -> unconfigured.upload(png()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.IMAGE_STORAGE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("空文件：参数校验失败")
    void emptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(empty))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PARAM_INVALID);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("超过 5 MB：图片过大")
    void tooLarge() {
        byte[] big = Arrays.copyOf(PNG_HEADER, (int) ImageUploadService.MAX_BYTES + 1);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", big);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.IMAGE_TOO_LARGE);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("魔数不是四种图片之一：类型不支持（即便扩展名与 Content-Type 都写成 png）")
    void unsupportedType() {
        MockMultipartFile html = new MockMultipartFile("file", "a.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes());

        assertThatThrownBy(() -> service.upload(html))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.IMAGE_TYPE_UNSUPPORTED);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("R2 写入抛 SDK 异常：翻译成图片存储写入失败")
    void storageFailure() {
        when(storage.put(anyString(), anyString(), any()))
                .thenThrow(SdkClientException.create("connect timeout"));

        assertThatThrownBy(() -> service.upload(png()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.IMAGE_STORAGE_ERROR);
    }
}
