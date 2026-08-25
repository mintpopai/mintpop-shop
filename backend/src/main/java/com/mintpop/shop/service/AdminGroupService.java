package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.request.AdminGroupUpsertRequest;
import com.mintpop.shop.response.AdminGroupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端分组服务：列表（含商品数）与新增/编辑/删除（仅空组可删）。
 */
@Service
@RequiredArgsConstructor
public class AdminGroupService {

    private final ProductGroupMapper productGroupMapper;
    private final ProductMapper productMapper;

    /** 全部分组（按排序号），附组内商品数（含下架） */
    public List<AdminGroupResponse> listGroups() {
        List<ProductGroup> groups = productGroupMapper.selectList(
                new LambdaQueryWrapper<ProductGroup>().orderByAsc(ProductGroup::getSortOrder));
        Map<Long, Long> countByGroup = productMapper.selectMaps(new QueryWrapper<Product>()
                        .select("group_id AS groupId", "COUNT(*) AS cnt")
                        .groupBy("group_id")).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("groupId")).longValue(),
                        row -> ((Number) row.get("cnt")).longValue()));
        return groups.stream()
                .map(g -> AdminGroupResponse.of(g, countByGroup.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    /** 新增分组 */
    public AdminGroupResponse createGroup(AdminGroupUpsertRequest request) {
        ProductGroup group = new ProductGroup();
        apply(group, request);
        productGroupMapper.insert(group);
        return AdminGroupResponse.of(group, 0L);
    }

    /** 编辑分组 */
    public AdminGroupResponse updateGroup(Long id, AdminGroupUpsertRequest request) {
        ProductGroup group = productGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(BizCodeEnum.GROUP_NOT_FOUND);
        }
        apply(group, request);
        productGroupMapper.updateById(group);
        return AdminGroupResponse.of(group, countProducts(id));
    }

    /** 删除分组：组内还有商品（含下架）时拒绝，先移走/清空再删 */
    public void deleteGroup(Long id) {
        if (productGroupMapper.selectById(id) == null) {
            throw new BizException(BizCodeEnum.GROUP_NOT_FOUND);
        }
        if (countProducts(id) > 0) {
            throw new BizException(BizCodeEnum.GROUP_NOT_EMPTY);
        }
        productGroupMapper.deleteById(id);
    }

    private long countProducts(Long groupId) {
        return productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getGroupId, groupId));
    }

    private void apply(ProductGroup group, AdminGroupUpsertRequest request) {
        group.setNameZh(request.getNameZh().trim());
        // name_en 是 NOT NULL DEFAULT ''：空白落空串而非 null，否则清空英文名写不进数据库
        group.setNameEn(request.getNameEn() == null || request.getNameEn().isBlank()
                ? "" : request.getNameEn().trim());
        group.setSortOrder(request.getSortOrder());
    }
}
