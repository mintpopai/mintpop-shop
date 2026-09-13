package ai.mintpop.shop.controller;

import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.exception.BizException;
import ai.mintpop.shop.exception.GlobalExceptionHandler;
import ai.mintpop.shop.response.ProductDetailResponse;
import ai.mintpop.shop.service.ProductService;
import ai.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

    private MockMvc mockMvc;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler(TestMessages.create()))
                .build();
    }

    @Test
    @DisplayName("GET /api/products/{id} 返回 code 0 与商品详情")
    void returnsProductDetail() throws Exception {
        when(productService.getOnSaleProduct(11L)).thenReturn(new ProductDetailResponse(
                11L, "薄荷精灵盲盒", "经典款", "<p>详情</p>", 5900L, null, "经典款", "MINT"));

        mockMvc.perform(get("/api/products/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("薄荷精灵盲盒"))
                .andExpect(jsonPath("$.data.detail").value("<p>详情</p>"))
                .andExpect(jsonPath("$.data.priceCents").value(5900));
    }

    @Test
    @DisplayName("商品不存在：HTTP 200 + 业务码 210002")
    void missingProductReturnsBizCode() throws Exception {
        when(productService.getOnSaleProduct(9L)).thenThrow(new BizException(BizCodeEnum.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/products/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210002));
    }
}
