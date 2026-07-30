package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminOrderItemResponse;
import com.mintpop.shop.response.PageResponse;
import com.mintpop.shop.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端订单查询服务：全量订单分页 + 状态筛选 + 订单号前缀搜索。
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    /** 单页上限，防误传大页拖库 */
    private static final long MAX_PAGE_SIZE = 100;

    private final ShopOrderMapper shopOrderMapper;
    private final ProductMapper productMapper;
    private final ShopUserMapper shopUserMapper;
    private final MessageSource messageSource;

    /**
     * 分页查询订单：status 为空不过滤；keyword 按订单号前缀匹配（订单号前缀固定，前缀索引友好）。
     */
    public PageResponse<AdminOrderItemResponse> pageOrders(long page, long size, String status, String keyword) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .orderByDesc(ShopOrder::getCreatedAt)
                .orderByDesc(ShopOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ShopOrder::getStatus, parseStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.likeRight(ShopOrder::getOrderNo, keyword.trim());
        }

        Page<ShopOrder> result = shopOrderMapper.selectPage(Page.of(safePage, safeSize), wrapper);
        return new PageResponse<>(assemble(result.getRecords()), result.getTotal(), safePage, safeSize);
    }

    /**
     * 订单实体组装为管理端列表项：批量补商品名（按请求语言）与买家邮箱。
     * 概览页最近订单同样走这里，组装口径全仓唯一。
     */
    public List<AdminOrderItemResponse> assemble(List<ShopOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Locale locale = LocaleContextHolder.getLocale();
        boolean english = I18nUtil.isEnglish();

        Set<Long> productIds = orders.stream().map(ShopOrder::getProductId).collect(Collectors.toSet());
        Map<Long, String> productNameById = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId,
                        p -> I18nUtil.pick(english, p.getNameEn(), p.getNameZh())));

        Set<Long> userIds = orders.stream().map(ShopOrder::getUserId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> emailById = userIds.isEmpty() ? Map.of()
                : shopUserMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(ShopUser::getId, ShopUser::getEmail));

        String deletedPlaceholder = messageSource.getMessage("order.product-deleted", null, locale);
        return orders.stream()
                .map(o -> new AdminOrderItemResponse(
                        o.getOrderNo(),
                        productNameById.getOrDefault(o.getProductId(), deletedPlaceholder),
                        o.getUserId() == null ? null : emailById.get(o.getUserId()),
                        o.getQuantity(),
                        o.getAmountCents(),
                        o.getStatus().name(),
                        messageSource.getMessage(o.getStatus().getLabelKey(), null, locale),
                        o.getPaymentProvider(),
                        o.getCreatedAt().toInstant(ZoneOffset.UTC),
                        o.getPaidAt() == null ? null : o.getPaidAt().toInstant(ZoneOffset.UTC)))
                .toList();
    }

    /** 状态参数解析：非法取值按参数校验失败处理 */
    private OrderStatusEnum parseStatus(String status) {
        try {
            return OrderStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }
    }
}
