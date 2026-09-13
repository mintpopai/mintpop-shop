package ai.mintpop.shop.controller;

import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.exception.BizException;
import ai.mintpop.shop.exception.GlobalExceptionHandler;
import ai.mintpop.shop.response.ImageUploadResponse;
import ai.mintpop.shop.service.ImageUploadService;
import ai.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUploadControllerTest {

    private MockMvc mockMvc;
    private ImageUploadService imageUploadService;

    @BeforeEach
    void setUp() {
        imageUploadService = mock(ImageUploadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUploadController(imageUploadService))
                .setControllerAdvice(new GlobalExceptionHandler(TestMessages.create()))
                .build();
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "cat.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("上传成功：返回公开 URL")
    void uploadsAndReturnsUrl() throws Exception {
        when(imageUploadService.upload(any()))
                .thenReturn(new ImageUploadResponse("https://shop-assets.mintpop.ai/products/2026/09/a.png"));

        mockMvc.perform(multipart("/api/admin/uploads/images").file(file())
                        .header("Accept-Language", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value("https://shop-assets.mintpop.ai/products/2026/09/a.png"));
    }

    @Test
    @DisplayName("业务异常：HTTP 200 + 业务码与中文文案")
    void bizErrorMappedToApiResponse() throws Exception {
        when(imageUploadService.upload(any()))
                .thenThrow(new BizException(BizCodeEnum.IMAGE_TYPE_UNSUPPORTED));

        mockMvc.perform(multipart("/api/admin/uploads/images").file(file())
                        .header("Accept-Language", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210006))
                .andExpect(jsonPath("$.msg").value("只支持 JPEG、PNG、WebP、GIF 图片"));
    }

    @Test
    @DisplayName("不带 file part：HTTP 200 + 参数错误业务码，不落到系统繁忙")
    void missingFilePartMappedToParamInvalid() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/images")
                        .header("Accept-Language", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));
    }
}
