package com.mintpop.shop.controller;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.service.GroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认语言回归测试：验证「请求未带 Accept-Language 时落到中文兜底」这条链路真实生效
 * （application.yml 的 spring.web.locale=zh-CN → AcceptHeaderLocaleResolver 默认值 →
 * GlobalExceptionHandler 按 LocaleContextHolder 解析文案）。
 * 用真实 @WebMvcTest 切片（而非 standalone MockMvc）是因为 standalone 手工装配不会加载
 * WebMvcAutoConfiguration/MessageSourceAutoConfiguration，测不出 application.yml 的默认语言配置。
 * 安全过滤器与本测试意图无关，用 addFilters=false 绕开（GroupController 无 @CurrentUserId 参数，
 * 切片内也未导入 SecurityConfig）。
 */
@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class DefaultLocaleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    // WebConfig 注册的管理端拦截器依赖 mapper，切片无 SqlSessionFactory，mock 兜底
    @MockitoBean
    private ShopUserMapper shopUserMapper;

    @Test
    @DisplayName("不带 Accept-Language 头：默认落到中文（spring.web.locale=zh-CN）")
    void defaultsToChineseWithoutAcceptLanguageHeader() throws Exception {
        when(groupService.listGroupsWithProducts())
                .thenThrow(new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210001))
                .andExpect(jsonPath("$.msg").value("商品不存在或已下架"));
    }

    @Test
    @DisplayName("带 Accept-Language: en-US：文案解析为英文")
    void resolvesEnglishWithAcceptLanguageHeader() throws Exception {
        when(groupService.listGroupsWithProducts())
                .thenThrow(new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));

        mockMvc.perform(get("/api/groups").header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210001))
                .andExpect(jsonPath("$.msg").value("Product not found or off sale"));
    }
}
