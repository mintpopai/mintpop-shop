package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.response.ProductDetailResponse;
import com.mintpop.shop.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商城端商品查询服务。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    /**
     * 查上架商品的详情。
     * 已下架与不存在一律报「商品不存在」——两者返回不同错误等于告诉外人「这个 ID 确实有商品，只是下架了」。
     */
    public ProductDetailResponse getOnSaleProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !Boolean.TRUE.equals(product.getOnSale())) {
            throw new BizException(BizCodeEnum.PRODUCT_NOT_FOUND);
        }
        return ProductDetailResponse.of(product, I18nUtil.isEnglish());
    }
}
