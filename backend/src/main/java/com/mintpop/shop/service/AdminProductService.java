package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.request.AdminProductUpsertRequest;
import com.mintpop.shop.response.AdminProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端商品服务：全量查询（含下架）与新增/编辑/上下架。
 * 不提供物理删除——订单引用商品，删除会破坏订单展示，下架即隐藏。
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductMapper productMapper;
    private final ProductGroupMapper productGroupMapper;

    /** 全部商品（含下架），可按分组过滤；按分组、新旧排列 */
    public List<AdminProductResponse> listProducts(Long groupId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .orderByAsc(Product::getGroupId)
                .orderByDesc(Product::getId);
        if (groupId != null) {
            wrapper.eq(Product::getGroupId, groupId);
        }
        return productMapper.selectList(wrapper).stream().map(AdminProductResponse::of).toList();
    }

    /** 新增商品（目标分组必须存在） */
    public AdminProductResponse createProduct(AdminProductUpsertRequest request) {
        ensureGroupExists(request.getGroupId());
        Product product = new Product();
        apply(product, request);
        productMapper.insert(product);
        return AdminProductResponse.of(product);
    }

    /** 编辑商品（商品与目标分组必须存在） */
    public AdminProductResponse updateProduct(Long id, AdminProductUpsertRequest request) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(BizCodeEnum.PRODUCT_NOT_FOUND);
        }
        ensureGroupExists(request.getGroupId());
        apply(product, request);
        productMapper.updateById(product);
        return AdminProductResponse.of(product);
    }

    /** 上/下架 */
    public AdminProductResponse setOnSale(Long id, boolean onSale) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(BizCodeEnum.PRODUCT_NOT_FOUND);
        }
        product.setOnSale(onSale);
        productMapper.updateById(product);
        return AdminProductResponse.of(product);
    }

    private void ensureGroupExists(Long groupId) {
        if (productGroupMapper.selectById(groupId) == null) {
            throw new BizException(BizCodeEnum.GROUP_NOT_FOUND);
        }
    }

    /** 请求体落到实体（新增/编辑共用）；空白可选字段一律归一成 null，避免库里混存空串 */
    private void apply(Product product, AdminProductUpsertRequest request) {
        product.setGroupId(request.getGroupId());
        product.setNameZh(request.getNameZh().trim());
        product.setNameEn(normalize(request.getNameEn()));
        product.setDescriptionZh(normalize(request.getDescriptionZh()));
        product.setDescriptionEn(normalize(request.getDescriptionEn()));
        product.setBadgeZh(normalize(request.getBadgeZh()));
        product.setBadgeEn(normalize(request.getBadgeEn()));
        product.setAccent(request.getAccent());
        product.setPriceCents(request.getPriceCents());
        product.setImageUrl(normalize(request.getImageUrl()));
        product.setOnSale(request.getOnSale());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
