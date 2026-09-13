package ai.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ai.mintpop.shop.client.StripeGateway;
import ai.mintpop.shop.config.OrderProperties;
import ai.mintpop.shop.entity.ShopOrder;
import ai.mintpop.shop.enumeration.OrderStatusEnum;
import ai.mintpop.shop.mapper.ShopOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单懒惰过期：无定时任务，读到超时未支付订单的入口（列表/发起支付/查单）顺手把它置 EXPIRED，
 * 并尽力而为取消 Stripe 侧 PaymentIntent（令残留支付页的 client_secret 失效）。
 * 只有 PENDING/FAILED 可过期；条件 UPDATE 防与入账/取消竞态，取消失败（已支付/处理中）
 * 时钱已收仍由 settlePaid 入账兜底。
 * 订单真正被置 EXPIRED 时（条件 UPDATE 生效）同事务内归还预占库存。
 */
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final ShopOrderMapper shopOrderMapper;
    private final StripeGateway stripeGateway;
    private final OrderProperties orderProperties;
    private final StockService stockService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 单笔懒惰过期：订单超时则条件置 EXPIRED 并撤 Stripe 侧凭据，返回是否已超时。
     * 返回值只看超时事实（createdAt 判定）——即便条件 UPDATE 因竞态影响 0 行
     * （已被入账/取消），订单同样不可再支付，调用方按「不可支付」处理即可。
     */
    public boolean expireIfTimedOut(ShopOrder order) {
        if (order.getCreatedAt() == null || order.getCreatedAt().isAfter(cutoff())) {
            return false;
        }
        expire(order);
        return true;
    }

    /**
     * 批量懒惰过期：把该用户超时的待支付/支付失败订单逐单置 EXPIRED（订单列表入口用）。
     * 逐单而非按 user_id 一条 UPDATE：批量改无法知道哪些单真的生效，
     * 而库存只能归还给「本次真正被置 EXPIRED」的单——查出后、UPDATE 前被 webhook 入账的单绝不能归还。
     * 一个用户的超时单只有几条，逐单开销可忽略。
     */
    public void expireTimedOut(Long userId) {
        List<ShopOrder> timedOut = shopOrderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, userId)
                .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED)
                .lt(ShopOrder::getCreatedAt, cutoff()));
        timedOut.forEach(this::expire);
    }

    /** 单笔过期：事务内条件置 EXPIRED，生效才归还预占库存；提交后尽力而为撤 Stripe 侧凭据 */
    private void expire(ShopOrder order) {
        transactionTemplate.execute(status -> {
            int rows = shopOrderMapper.update(null, new LambdaUpdateWrapper<ShopOrder>()
                    .eq(ShopOrder::getOrderNo, order.getOrderNo())
                    .in(ShopOrder::getStatus, OrderStatusEnum.PENDING, OrderStatusEnum.FAILED)
                    .set(ShopOrder::getStatus, OrderStatusEnum.EXPIRED));
            if (rows > 0) {
                stockService.release(order);
            }
            return null;
        });
        cancelIntentIfPresent(order);
    }

    /** 撤 Stripe 侧支付凭据：未发起过支付（无交易号）的单没有可撤对象 */
    private void cancelIntentIfPresent(ShopOrder order) {
        if (order.getPaymentTradeNo() != null) {
            stripeGateway.cancelPaymentIntent(order.getPaymentTradeNo());
        }
    }

    /** 剩余支付秒数（前端订单级倒计时用）：已超时为 0 不出负数；createdAt 为空按整个时限算（与不误杀口径一致） */
    public long remainingSeconds(ShopOrder order) {
        long limitSeconds = orderProperties.getExpireMinutes() * 60;
        if (order.getCreatedAt() == null) {
            return limitSeconds;
        }
        long elapsed = Duration.between(order.getCreatedAt(), LocalDateTime.now()).getSeconds();
        return Math.max(0, limitSeconds - elapsed);
    }

    /** 过期分界线：创建时间早于此刻的可过期订单视为超时 */
    private LocalDateTime cutoff() {
        return LocalDateTime.now().minusMinutes(orderProperties.getExpireMinutes());
    }
}
