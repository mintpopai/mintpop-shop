package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mintpop.shop.client.FeishuBotClient;
import com.mintpop.shop.config.NotifyProperties;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

/**
 * 订单事件飞书通知（推给店主的提醒，文案固定中文、不走 i18n）。
 * 尽力而为：任何异常只记日志，绝不影响支付主流程；未配置 webhook 时整体静默关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotifyService {

    private static final DateTimeFormatter PAID_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShopOrderMapper shopOrderMapper;
    private final ProductMapper productMapper;
    private final ShopUserMapper shopUserMapper;
    private final NotifyProperties notifyProperties;
    private final FeishuBotClient feishuBotClient;

    /**
     * 支付成功通知（异步）。调用点已保证每单只触发一次（settlePaid 条件 UPDATE 幂等），
     * 本方法自身消化全部异常，不依赖全局异常处理器。
     */
    @Async
    public void notifyOrderPaid(String orderNo) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            ShopOrder order = shopOrderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                    .eq(ShopOrder::getOrderNo, orderNo));
            if (order == null) {
                log.warn("支付成功通知查无此单，跳过 orderNo={}", orderNo);
                return;
            }
            feishuBotClient.sendCard("MintPop Shop 新订单已支付", buildFields(order));
        } catch (Exception e) {
            log.warn("支付成功飞书通知失败（不影响入账）orderNo={}", orderNo, e);
        }
    }

    /** 组装卡片字段（有序）：订单号、商品×数量、金额、买家、交易号、支付时间 */
    private LinkedHashMap<String, String> buildFields(ShopOrder order) {
        Product product = productMapper.selectById(order.getProductId());
        ShopUser user = order.getUserId() == null ? null
                : shopUserMapper.selectById(order.getUserId());
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("订单号", order.getOrderNo());
        fields.put("商品", (product == null ? "未知商品" : product.getNameZh())
                + " × " + order.getQuantity());
        // 金额库里存美分（最小货币单位），缩位两位小数展示为美元
        fields.put("金额", "$" + BigDecimal.valueOf(order.getAmountCents(), 2).toPlainString());
        fields.put("买家", buyerDisplay(user));
        fields.put("交易号", order.getPaymentTradeNo());
        fields.put("支付时间", order.getPaidAt() == null ? "-"
                : PAID_AT_FORMAT.format(order.getPaidAt()));
        return fields;
    }

    /** 下单强制登录，买家理论必有值；查无用户（存量游客单等边界）兜底显示「未知买家」 */
    private String buyerDisplay(ShopUser user) {
        if (user == null) {
            return "未知买家";
        }
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            return user.getEmail();
        }
        return user.getNickname() + " (" + user.getEmail() + ")";
    }
}
