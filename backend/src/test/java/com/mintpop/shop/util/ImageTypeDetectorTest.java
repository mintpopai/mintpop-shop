package com.mintpop.shop.util;

import com.mintpop.shop.enumeration.ImageTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    @DisplayName("JPEG：FF D8 FF 开头")
    void detectsJpeg() {
        assertThat(ImageTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0)))
                .contains(ImageTypeEnum.JPEG);
    }

    @Test
    @DisplayName("PNG：8 字节签名")
    void detectsPng() {
        assertThat(ImageTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)))
                .contains(ImageTypeEnum.PNG);
    }

    @Test
    @DisplayName("GIF：GIF87a 与 GIF89a 都认")
    void detectsGif() {
        assertThat(ImageTypeDetector.detect("GIF89a......".getBytes(StandardCharsets.US_ASCII)))
                .contains(ImageTypeEnum.GIF);
        assertThat(ImageTypeDetector.detect("GIF87a......".getBytes(StandardCharsets.US_ASCII)))
                .contains(ImageTypeEnum.GIF);
    }

    @Test
    @DisplayName("WebP：RIFF....WEBP")
    void detectsWebp() {
        assertThat(ImageTypeDetector.detect("RIFF\0\0\0\0WEBP......".getBytes(StandardCharsets.US_ASCII)))
                .contains(ImageTypeEnum.WEBP);
    }

    @Test
    @DisplayName("RIFF 开头但不是 WEBP（如 WAV）不算图片")
    void riffWithoutWebpRejected() {
        assertThat(ImageTypeDetector.detect("RIFF\0\0\0\0WAVE......".getBytes(StandardCharsets.US_ASCII)))
                .isEmpty();
    }

    @Test
    @DisplayName("伪装成图片的 HTML / 空内容 / 不足 12 字节一律不认")
    void rejectsNonImages() {
        assertThat(ImageTypeDetector.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(ImageTypeDetector.detect(bytes(0xFF, 0xD8))).isEmpty();
    }

    @Test
    @DisplayName("枚举携带写入 R2 用的 MIME 与扩展名")
    void enumCarriesMimeAndExtension() {
        assertThat(ImageTypeEnum.JPEG.getMimeType()).isEqualTo("image/jpeg");
        assertThat(ImageTypeEnum.JPEG.getExtension()).isEqualTo("jpg");
        assertThat(ImageTypeEnum.WEBP.getMimeType()).isEqualTo("image/webp");
        assertThat(ImageTypeEnum.WEBP.getExtension()).isEqualTo("webp");
    }
}
