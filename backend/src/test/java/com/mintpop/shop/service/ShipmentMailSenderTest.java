package com.mintpop.shop.service;

import com.mintpop.shop.config.AppMailProperties;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.entity.ShopUser;
import com.mintpop.shop.support.TestMessages;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipmentMailSenderTest {

    private JavaMailSender mailSender;
    private ShipmentMailSender sender;
    private AppMailProperties properties;

    /** 真实模板引擎：断言渲染结果，模板写错这里就红 */
    private static SpringTemplateEngine realEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(TestMessages.create());
        return engine;
    }

    @SuppressWarnings("unchecked")
    private ShipmentMailSender build(JavaMailSender available) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        return new ShipmentMailSender(provider, realEngine(), properties, TestMessages.create());
    }

    @BeforeEach
    void setUp() {
        properties = new AppMailProperties();
        properties.setFrom("shop@mintpop.ai");
        properties.setFromName("MintPop Shop");
        properties.setSiteBaseUrl("https://mintpop.ai");
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        sender = build(mailSender);
    }

    private ShopOrder order() {
        ShopOrder o = new ShopOrder();
        o.setOrderNo("mintpopshop_20260731120000123456");
        o.setQuantity(2);
        o.setAmountCents(1999L);
        return o;
    }

    private ShopUser buyer() {
        ShopUser u = new ShopUser();
        u.setEmail("buyer@example.com");
        u.setNickname("小明");
        return u;
    }

    @Test
    @DisplayName("中文买家：主题与正文用中文，发货内容与订单号出现在正文里")
    void sendsChineseMail() throws Exception {
        MimeMessage[] captured = new MimeMessage[1];
        when(mailSender.createMimeMessage()).thenAnswer(inv -> {
            captured[0] = new JavaMailSenderImpl().createMimeMessage();
            return captured[0];
        });

        MailResult result = sender.send(order(), "会员账号", buyer(), "兑换码：ABC-123", Locale.SIMPLIFIED_CHINESE);

        assertThat(result.sent()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(captured[0].getSubject()).contains("发货").contains("mintpopshop_20260731120000123456");
        assertThat(captured[0].getAllRecipients()[0].toString()).isEqualTo("buyer@example.com");
        String body = captured[0].getContent().toString();
        assertThat(body).contains("兑换码：ABC-123").contains("会员账号");
    }

    @Test
    @DisplayName("英文买家：主题走英文文案")
    void sendsEnglishMail() throws Exception {
        MimeMessage[] captured = new MimeMessage[1];
        when(mailSender.createMimeMessage()).thenAnswer(inv -> {
            captured[0] = new JavaMailSenderImpl().createMimeMessage();
            return captured[0];
        });

        MailResult result = sender.send(order(), "Membership", buyer(), "Code: ABC-123", Locale.US);

        assertThat(result.sent()).isTrue();
        assertThat(captured[0].getSubject()).contains("shipped");
    }

    @Test
    @DisplayName("SMTP 抛异常：返回失败且不外抛")
    void swallowsSendFailure() {
        doThrow(new MailSendException("connect timed out")).when(mailSender).send(any(MimeMessage.class));

        MailResult result = sender.send(order(), "会员账号", buyer(), "兑换码：ABC-123", Locale.SIMPLIFIED_CHINESE);

        assertThat(result.sent()).isFalse();
        assertThat(result.error()).contains("connect timed out");
    }

    @Test
    @DisplayName("未配置 spring.mail.host：JavaMailSender 不存在，返回未配置失败")
    void reportsNotConfigured() {
        ShipmentMailSender noMail = build(null);

        MailResult result = noMail.send(order(), "会员账号", buyer(), "兑换码：ABC-123", Locale.SIMPLIFIED_CHINESE);

        assertThat(result.sent()).isFalse();
        assertThat(result.error()).isEqualTo("邮件服务未配置，请联系管理员");
    }

    @Test
    @DisplayName("配了 SMTP 却漏配 app.mail.from：同样按未配置返回可读文案，不抛底层的 From address must not be null")
    void reportsNotConfiguredWhenFromMissing() {
        properties.setFrom(null);

        MailResult result = build(mailSender).send(
                order(), "会员账号", buyer(), "兑换码：ABC-123", Locale.SIMPLIFIED_CHINESE);

        assertThat(result.sent()).isFalse();
        assertThat(result.error()).isEqualTo("邮件服务未配置，请联系管理员");
    }

    @Test
    @DisplayName("app.mail.from 是空白串：与漏配等价，按未配置处理")
    void reportsNotConfiguredWhenFromBlank() {
        properties.setFrom("  ");

        MailResult result = build(mailSender).send(
                order(), "Membership", buyer(), "Code: ABC-123", Locale.US);

        assertThat(result.sent()).isFalse();
        assertThat(result.error()).isEqualTo("Email service is not configured, please contact the administrator");
    }
}
