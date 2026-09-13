package ai.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ai.mintpop.shop.entity.Product;
import ai.mintpop.shop.entity.ShopOrder;
import ai.mintpop.shop.enumeration.BizCodeEnum;
import ai.mintpop.shop.enumeration.StockHoldEnum;
import ai.mintpop.shop.exception.BizException;
import ai.mintpop.shop.mapper.ProductMapper;
import ai.mintpop.shop.mapper.ShopOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 库存服务：下单预占、取消/过期归还、入账成交。
 *
 * <p>每个操作都是「订单 stock_hold 条件 UPDATE + 商品 stock 相对 UPDATE」两步：先用订单上的占用状态
 * 当幂等钥匙（HELD→RELEASED、HELD→CONSUMED 各只能成功一次），钥匙拿到了才动商品库存。
 * 本类不开事务，由调用方（下单 / 取消 / 过期 / 入账）把这两步与订单状态变更包进同一个 TransactionTemplate。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductMapper productMapper;
    private final ShopOrderMapper shopOrderMapper;

    /**
     * 预占：商品不限库存（stock 为 null）直接返回 NONE 不碰库存；
     * 否则条件扣减，0 行即库存不足（或商品在读取与扣减之间被改成了不限——极罕见，按不足让用户重试）。
     *
     * @return 应写入订单的占用状态
     */
    public StockHoldEnum reserve(Product product, int quantity) {
        if (product.getStock() == null) {
            return StockHoldEnum.NONE;
        }
        if (productMapper.reserveStock(product.getId(), quantity) == 0) {
            throw new BizException(BizCodeEnum.PRODUCT_OUT_OF_STOCK);
        }
        return StockHoldEnum.HELD;
    }

    /** 归还：只有真正持有预占（HELD）的单才加回去；未预占或已归还的单 0 行即返回，重复调用幂等 */
    public void release(ShopOrder order) {
        if (transitionHold(order.getId(), StockHoldEnum.HELD, StockHoldEnum.RELEASED) == 0) {
            return;
        }
        productMapper.releaseStock(order.getProductId(), order.getQuantity());
    }

    /**
     * 成交：预占中的单只需把状态钉成 CONSUMED（扣减在预占时已经发生）。
     * 钉不上说明预占不在了：重查若是 RELEASED（取消/过期与付款竞态，库存已被加回），
     * 钱已经收到、订单必须成立，于是补扣一次（允许扣成负数，前台按售罄处理）；
     * 是 NONE（不限库存商品）或已 CONSUMED 则什么都不做。
     */
    public void consume(ShopOrder order) {
        if (transitionHold(order.getId(), StockHoldEnum.HELD, StockHoldEnum.CONSUMED) > 0) {
            return;
        }
        ShopOrder latest = shopOrderMapper.selectById(order.getId());
        if (latest == null || latest.getStockHold() != StockHoldEnum.RELEASED) {
            return;
        }
        if (transitionHold(order.getId(), StockHoldEnum.RELEASED, StockHoldEnum.CONSUMED) == 0) {
            return;
        }
        productMapper.deductStock(order.getProductId(), order.getQuantity());
        log.warn("订单 {} 的预占已在取消/过期时归还，但随后收到付款，已补扣库存 {} 件（商品 {} 可能出现负库存，请人工核对）",
                order.getOrderNo(), order.getQuantity(), order.getProductId());
    }

    /** 订单占用状态条件迁移：where 锁 id + 当前状态，返回影响行数（0 = 钥匙已被别人拿走或本就不在该状态） */
    private int transitionHold(Long orderId, StockHoldEnum from, StockHoldEnum to) {
        return shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getId, orderId)
                .eq(ShopOrder::getStockHold, from)
                .set(ShopOrder::getStockHold, to));
    }
}
