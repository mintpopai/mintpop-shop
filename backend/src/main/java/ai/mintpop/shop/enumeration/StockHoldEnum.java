package ai.mintpop.shop.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单对库存的占用状态（列 shop_order.stock_hold）：成员名与持久化字符串取值逐字一致。
 * 归还与成交都按它做条件 UPDATE，取消 / 懒惰过期 / Stripe 入账三条路径并发时互斥且幂等。
 */
@Getter
@AllArgsConstructor
public enum StockHoldEnum {

    /** 未预占：下单时商品不限库存，或库存功能上线前的存量订单 */
    NONE("NONE"),
    /** 预占中：下单时已从 product.stock 扣掉 quantity */
    HELD("HELD"),
    /** 已归还：订单取消或过期，quantity 已加回 product.stock */
    RELEASED("RELEASED"),
    /** 已成交：支付成功，预占转为实售（或竞态下已补扣） */
    CONSUMED("CONSUMED");

    /** 持久化到数据库的取值 */
    @EnumValue
    private final String value;
}
