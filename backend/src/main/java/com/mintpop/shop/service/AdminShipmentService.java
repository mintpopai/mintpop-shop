package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mintpop.shop.config.AppMailProperties;
import com.mintpop.shop.entity.OrderShipment;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.enumeration.ShipmentEmailStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.OrderShipmentMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.mapper.ShopUserMapper;
import com.mintpop.shop.response.AdminShipmentItemResponse;
import com.mintpop.shop.response.AdminShipmentResponse;
import com.mintpop.shop.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端发货服务：一单可多次发货，全部留痕；重新发货必须填原因。
 * 邮件是 IO，放在事务外同步发送——发货已经落库，邮件失败只记录状态，绝不回滚。
 * 事务提交之后只做「发信 + 记账」两件事，不再有任何一次 DB 读/写会抛出后仍
 * 让「发货已落库」这一事实无法被感知（写库前需要的只读查询一律提前到事务之前）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminShipmentService {

    /** email_error 列宽 512，入库前截断 */
    private static final int MAX_EMAIL_ERROR_LENGTH = 512;

    private final ShopOrderMapper shopOrderMapper;
    private final OrderShipmentMapper orderShipmentMapper;
    private final ProductMapper productMapper;
    private final ShopUserMapper shopUserMapper;
    private final ShipmentMailSender shipmentMailSender;
    private final TransactionTemplate transactionTemplate;
    private final MessageSource messageSource;
    private final AppMailProperties mailProperties;

    /**
     * 发货：校验 → 查商品名（只读，须在写库前完成）→ 事务内（订单置已完成 + 插记录，
     * 邮件状态先记 FAILED）→ 事务提交后同步发信 → 回写邮件状态。
     */
    public AdminShipmentResponse ship(String orderNo, String content, String reason, Long operatorUserId) {
        ShopOrder order = requireOrder(orderNo);
        if (order.getStatus() != OrderStatusEnum.PAID && order.getStatus() != OrderStatusEnum.COMPLETED) {
            throw new BizException(BizCodeEnum.ORDER_NOT_SHIPPABLE);
        }
        ShopUser buyer = order.getUserId() == null ? null : shopUserMapper.selectById(order.getUserId());
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            // 发货靠邮件送达，收不到就没有发货这回事
            throw new BizException(BizCodeEnum.USER_NOT_FOUND);
        }
        boolean shippedBefore = orderShipmentMapper.selectCount(new LambdaQueryWrapper<OrderShipment>()
                .eq(OrderShipment::getOrderId, order.getId())) > 0;
        if (shippedBefore && (reason == null || reason.isBlank())) {
            throw new BizException(BizCodeEnum.SHIPMENT_REASON_REQUIRED);
        }

        String trimmedContent = content.trim();
        // 商品名查询必须放在写库之前：它本身是一次 DB 读，若挪到事务提交之后再查，
        // 一旦此刻 DB 抖动抛异常就会直穿到控制器——但发货记录其实已经落库，
        // 变成一条 email_status=FAILED、从未真正发信的「幽灵记录」。挪到这里之后，
        // 事务提交后就只剩「发信 + 记账」两件事，不再有任何抛出点。
        Locale locale = resolveLocale(buyer);
        String productName = productName(order, locale);
        OrderShipment shipment = persist(order, buyer, trimmedContent, reason, operatorUserId);

        MailResult mailResult = shipmentMailSender.send(order, productName, buyer, trimmedContent, locale);
        // 不复用/回改已插入的 shipment 实体：insert() 已把该引用交给 mapper，
        // 事后再改它的字段会连带影响调用方对「插入时状态」的观察，这里只按 id 回写一条独立的更新记录
        String emailError = markMailResult(shipment.getId(), mailResult);

        return new AdminShipmentResponse(
                shipment.getCreatedAt().toInstant(ZoneOffset.UTC),
                (mailResult.sent() ? ShipmentEmailStatusEnum.SENT : ShipmentEmailStatusEnum.FAILED).getValue(),
                emailError);
    }

    /** 某订单的全部发货记录，时间倒序，带操作人邮箱 */
    public List<AdminShipmentItemResponse> listShipments(String orderNo) {
        ShopOrder order = requireOrder(orderNo);
        List<OrderShipment> shipments = orderShipmentMapper.selectList(
                new LambdaQueryWrapper<OrderShipment>()
                        .eq(OrderShipment::getOrderId, order.getId())
                        .orderByDesc(OrderShipment::getId));
        if (shipments.isEmpty()) {
            return List.of();
        }
        Set<Long> operatorIds = shipments.stream()
                .map(OrderShipment::getOperatorUserId).collect(Collectors.toSet());
        Map<Long, String> emailById = shopUserMapper.selectByIds(operatorIds).stream()
                .collect(Collectors.toMap(ShopUser::getId, ShopUser::getEmail));
        return shipments.stream()
                .map(s -> new AdminShipmentItemResponse(
                        s.getId(),
                        s.getContent(),
                        s.getReason(),
                        emailById.get(s.getOperatorUserId()),
                        s.getEmailTo(),
                        s.getEmailStatus().getValue(),
                        s.getEmailError(),
                        s.getCreatedAt().toInstant(ZoneOffset.UTC)))
                .toList();
    }

    /** 事务内两写：订单置已完成 + 插发货记录（邮件状态先记失败，发出去了再改） */
    private OrderShipment persist(ShopOrder order, ShopUser buyer, String content,
                                  String reason, Long operatorUserId) {
        return transactionTemplate.execute(status -> {
            if (order.getStatus() != OrderStatusEnum.COMPLETED) {
                order.setStatus(OrderStatusEnum.COMPLETED);
                shopOrderMapper.updateById(order);
            }
            OrderShipment shipment = new OrderShipment();
            shipment.setOrderId(order.getId());
            shipment.setContent(content);
            shipment.setReason(reason == null || reason.isBlank() ? null : reason.trim());
            shipment.setOperatorUserId(operatorUserId);
            shipment.setEmailTo(buyer.getEmail());
            shipment.setEmailStatus(ShipmentEmailStatusEnum.FAILED);
            // JVM 默认时区已钉 UTC，这里的挂钟时间与库内口径同源
            shipment.setCreatedAt(LocalDateTime.now());
            orderShipmentMapper.insert(shipment);
            return shipment;
        });
    }

    /**
     * 按发信结果回写记录：成功改 SENT，失败补失败原因；返回截断后的失败原因（成功为空）。
     * 此时发货已经落库、邮件也已经发出（或已经失败），这条更新只是「记账」性质的收尾动作。
     * 若这里的 updateById 本身再抛异常（如 DB 抖动），绝不能让它冒泡到 ship()：一旦外抛，
     * 控制器会把「已经成功发货」的这次操作显示成「发货失败」，管理员大概率会再点一次发货，
     * 导致买家收到两封含兑换码/账号密码的邮件——这比「邮件状态没回写成功」严重得多。
     * 所以这里只记 warn 留痕供人工对账，吞掉异常，让 ship() 照常按本次 mailResult 返回。
     */
    private String markMailResult(Long shipmentId, MailResult mailResult) {
        OrderShipment update = new OrderShipment();
        update.setId(shipmentId);
        String error = null;
        if (mailResult.sent()) {
            update.setEmailStatus(ShipmentEmailStatusEnum.SENT);
        } else {
            error = truncate(mailResult.error());
            update.setEmailStatus(ShipmentEmailStatusEnum.FAILED);
            update.setEmailError(error);
        }
        try {
            orderShipmentMapper.updateById(update);
        } catch (RuntimeException e) {
            log.warn("发货记录 id={} 的邮件状态回写失败，本次邮件结果 sent={} error={}，需人工核对该记录的 email_status 是否与实际相符",
                    shipmentId, mailResult.sent(), error, e);
        }
        return error;
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_EMAIL_ERROR_LENGTH ? error : error.substring(0, MAX_EMAIL_ERROR_LENGTH);
    }

    /**
     * 邮件语言：买家偏好优先，未设置回退配置的默认语言（{@code app.mail.default-locale}）。
     * 不能回退当前请求语言：这里的「当前请求」是管理员发起的发货请求，与买家语言无关
     * （管理端固定用 zh-CN 调后端，回退会让英文买家在偏好补写前收到中文邮件）。
     */
    private Locale resolveLocale(ShopUser buyer) {
        String preference = buyer.getLocale();
        return preference == null || preference.isBlank()
                ? Locale.forLanguageTag(mailProperties.getDefaultLocale()) : Locale.forLanguageTag(preference);
    }

    /** 商品名按邮件语言取，商品已删除时给占位文案 */
    private String productName(ShopOrder order, Locale locale) {
        Product product = productMapper.selectById(order.getProductId());
        if (product == null) {
            return messageSource.getMessage("order.product-deleted", null, locale);
        }
        return I18nUtil.pick("en".equals(locale.getLanguage()), product.getNameEn(), product.getNameZh());
    }

    private ShopOrder requireOrder(String orderNo) {
        ShopOrder order = shopOrderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(BizCodeEnum.ORDER_NOT_FOUND);
        }
        return order;
    }
}
