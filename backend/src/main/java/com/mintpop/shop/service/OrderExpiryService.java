package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mintpop.shop.client.StripeGateway;
import com.mintpop.shop.config.OrderProperties;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.mapper.ShopOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单懒惰过期：无定时任务，读到超时未支付订单的入口（列表/发起支付/查单）顺手把它置 EXPIRED，
 * 并尽力而为取消 Stripe 侧 PaymentIntent（令残留支付页的 client_secret 失效）。
 * 只有 PENDING/FAILED 可过期；条件 UPDATE 防与入账/取消竞态，取消失败（已支付/处理中）
 * 时钱已收仍由 settlePaid 入账兜底。
 */
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final ShopOrderMapper shopOrderMapper;
    private final StripeGateway stripeGateway;
    private final OrderProperties orderProperties;

    /**
     * 单笔懒惰过期：订单超时则条件置 EXPIRED 并撤 Stripe 侧凭据，返回是否已超时。
     * 返回值只看超时事实（createdAt 判定）——即便条件 UPDATE 因竞态影响 0 行
     * （已被入账/取消），订单同样不可再支付，调用方按「不可支付」处理即可。
     */
    public boolean expireIfTimedOut(ShopOrder order) {
        if (order.getCreatedAt() == null || order.getCreatedAt().isAfter(cutoff())) {
            return false;
        }
        shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, order.getOrderNo())
                .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED)
                .set(ShopOrder::getStatus, OrderStatusEnum.EXPIRED));
        cancelIntentIfPresent(order);
        return true;
    }

    /** 批量懒惰过期：把该用户超时的待支付/支付失败订单一次置 EXPIRED（订单列表入口用） */
    public void expireTimedOut(Long userId) {
        LocalDateTime cutoff = cutoff();
        // 先查后改：UPDATE 无法带回受影响行，取消 Stripe 侧凭据需要知道各单的交易号
        List<ShopOrder> timedOut = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED)
                .lt(ShopOrder::getCreatedAt, cutoff));
        if (timedOut.isEmpty()) {
            return;
        }
        shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED)
                .lt(ShopOrder::getCreatedAt, cutoff)
                .set(ShopOrder::getStatus, OrderStatusEnum.EXPIRED));
        timedOut.forEach(this::cancelIntentIfPresent);
    }

    /** 撤 Stripe 侧支付凭据：未发起过支付（无交易号）的单没有可撤对象 */
    private void cancelIntentIfPresent(ShopOrder order) {
        if (order.getPaymentTradeNo() != null) {
            stripeGateway.cancelPaymentIntent(order.getPaymentTradeNo());
        }
    }

    /** 过期分界线：创建时间早于此刻的可过期订单视为超时 */
    private LocalDateTime cutoff() {
        return LocalDateTime.now().minusMinutes(orderProperties.getExpireMinutes());
    }
}
