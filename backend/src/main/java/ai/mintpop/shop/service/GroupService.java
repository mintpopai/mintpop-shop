package ai.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ai.mintpop.shop.entity.Product;
import ai.mintpop.shop.entity.ProductGroup;
import ai.mintpop.shop.mapper.ProductGroupMapper;
import ai.mintpop.shop.mapper.ProductMapper;
import ai.mintpop.shop.response.GroupWithProductsResponse;
import ai.mintpop.shop.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分组查询服务。
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final ProductGroupMapper productGroupMapper;
    private final ProductMapper productMapper;

    /**
     * 查询全部分组及各组上架商品（骨架阶段数据量小，一次拉全）。
     */
    public List<GroupWithProductsResponse> listGroupsWithProducts() {
        List<ProductGroup> groups = productGroupMapper.selectList(
                new LambdaQueryWrapper<ProductGroup>().orderByAsc(ProductGroup::getSortOrder));
        List<Product> onSaleProducts = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getOnSale, true));
        Map<Long, List<Product>> productsByGroup = onSaleProducts.stream()
                .collect(Collectors.groupingBy(Product::getGroupId));
        boolean english = I18nUtil.isEnglish();
        return groups.stream()
                .map(g -> GroupWithProductsResponse.of(
                        g, productsByGroup.getOrDefault(g.getId(), List.of()), english))
                .toList();
    }
}
