package com.mintpop.shop.service;

import com.mintpop.shop.config.AppMailProperties;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.enumeration.BizCodeEnum;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 发货邮件发送：只管「按语言渲染模板 + 发出去」，不懂发货业务规则。
 * 全程消化异常并以 MailResult 上报——邮件失败绝不影响已经落库的发货。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentMailSender {

    private static final String TEMPLATE = "mail/shipment";
    private static final DateTimeFormatter SHIPPED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TemplateEngine templateEngine;
    private final AppMailProperties mailProperties;
    private final MessageSource messageSource;

    /**
     * 发一封发货邮件。locale 决定主题与正文文案，发货内容原样带出。
     * 未配置 spring.mail.host（JavaMailSender bean 不存在）或 app.mail.from 时，均按「未配置」失败返回。
     */
    public MailResult send(ShopOrder order, String productName, ShopUser buyer, String content, Locale locale) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        // 邮件配置分属两段（SMTP 走 spring.mail.*、发信地址走 app.mail.from），少任一段都发不出信，
        // 一律按「未配置」给出同一句可读文案：漏了 from 时底层只会抛 "From address must not be null"，
        // 这句会被原样截进 order_shipment.email_error 展示给管理员，看不出该去补哪个配置项。
        // 空白串同样要拦：setFrom("  ") 不报错，会把一封发件人异常的信真发出去。
        if (mailSender == null || mailProperties.getFrom() == null || mailProperties.getFrom().isBlank()) {
            return MailResult.failed(
                    messageSource.getMessage(BizCodeEnum.MAIL_NOT_CONFIGURED.getMessageKey(), null, locale));
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.getFrom(), mailProperties.getFromName());
            helper.setTo(buyer.getEmail());
            helper.setSubject(messageSource.getMessage("mail.shipment.subject",
                    new Object[]{order.getOrderNo()}, locale));
            helper.setText(render(order, productName, buyer, content, locale), true);
            mailSender.send(message);
            return MailResult.ok();
        } catch (Exception e) {
            log.warn("发货邮件发送失败 orderNo={} to={}", order.getOrderNo(), buyer.getEmail(), e);
            return MailResult.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 渲染中英共用模板：文案由模板内 #{} 按 locale 解析，变量在此备齐 */
    private String render(ShopOrder order, String productName, ShopUser buyer, String content, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("nickname", displayName(buyer));
        context.setVariable("orderNo", order.getOrderNo());
        context.setVariable("productName", productName);
        context.setVariable("quantity", order.getQuantity());
        // 金额库里存美分（最小货币单位），缩位两位小数展示为美元
        context.setVariable("amount", "$" + BigDecimal.valueOf(order.getAmountCents(), 2).toPlainString());
        // 全链路 UTC：这里的挂钟时间即 UTC，展示时显式标注
        context.setVariable("shippedAt", SHIPPED_AT_FORMAT.format(LocalDateTime.now()) + " UTC");
        context.setVariable("content", content);
        context.setVariable("orderUrl", mailProperties.getSiteBaseUrl() + "/orders/" + order.getOrderNo());
        return templateEngine.process(TEMPLATE, context);
    }

    /** 昵称为空时用邮箱当称呼 */
    private String displayName(ShopUser buyer) {
        return buyer.getNickname() == null || buyer.getNickname().isBlank()
                ? buyer.getEmail() : buyer.getNickname();
    }
}
