package com.mintpop.shop.controller;

import com.mintpop.shop.request.AdminGroupUpsertRequest;
import com.mintpop.shop.response.AdminGroupResponse;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.service.AdminGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端分组接口（/api/admin/** 由 AdminInterceptor 统一裁决管理员身份）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminGroupController {

    private final AdminGroupService adminGroupService;

    /** 全部分组（含商品数） */
    @GetMapping("/groups")
    public ApiResponse<List<AdminGroupResponse>> listGroups() {
        return ApiResponse.success(adminGroupService.listGroups());
    }

    /** 新增分组 */
    @PostMapping("/groups")
    public ApiResponse<AdminGroupResponse> createGroup(
            @Valid @RequestBody AdminGroupUpsertRequest request) {
        return ApiResponse.success(adminGroupService.createGroup(request));
    }

    /** 编辑分组 */
    @PutMapping("/groups/{id}")
    public ApiResponse<AdminGroupResponse> updateGroup(@PathVariable Long id,
            @Valid @RequestBody AdminGroupUpsertRequest request) {
        return ApiResponse.success(adminGroupService.updateGroup(id, request));
    }

    /** 删除分组（仅空组） */
    @DeleteMapping("/groups/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        adminGroupService.deleteGroup(id);
        return ApiResponse.success();
    }
}
