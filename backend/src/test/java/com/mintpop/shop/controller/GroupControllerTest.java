package com.mintpop.shop.controller;

import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.GroupWithProductsResponse;
import com.mintpop.shop.response.ProductResponse;
import com.mintpop.shop.service.GroupService;
import com.mintpop.shop.support.TestMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {

    private MockMvc mockMvc;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(groupService))
                .setControllerAdvice(new GlobalExceptionHandler(TestMessages.create()))
                .build();
    }

    @Test
    @DisplayName("GET /api/groups 返回 code 0 与分组数据")
    void listGroupsReturnsData() throws Exception {
        when(groupService.listGroupsWithProducts()).thenReturn(List.of(
                new GroupWithProductsResponse(1L, "盲盒系列", List.of(
                        new ProductResponse(11L, "薄荷精灵盲盒", "经典款", 5900L, null)))));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("盲盒系列"))
                .andExpect(jsonPath("$.data[0].products[0].priceCents").value(5900))
                .andExpect(jsonPath("$.success").doesNotExist());
    }
}
