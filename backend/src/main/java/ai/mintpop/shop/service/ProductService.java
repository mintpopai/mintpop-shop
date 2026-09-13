package ai.mintpop.shop.service;

import ai.mintpop.shop.entity.Product;
import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.exception.BizException;
import ai.mintpop.shop.mapper.ProductMapper;
import ai.mintpop.shop.response.ProductDetailResponse;
import ai.mintpop.shop.util.I18nUtil;
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
