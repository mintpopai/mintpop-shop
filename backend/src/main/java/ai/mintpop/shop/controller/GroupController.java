package ai.mintpop.shop.controller;

import ai.mintpop.shop.response.ApiResponse;
import ai.mintpop.shop.response.GroupWithProductsResponse;
import ai.mintpop.shop.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分组接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /** 分组列表（含各组上架商品） */
    @GetMapping("/groups")
    public ApiResponse<List<GroupWithProductsResponse>> listGroups() {
        return ApiResponse.success(groupService.listGroupsWithProducts());
    }
}
