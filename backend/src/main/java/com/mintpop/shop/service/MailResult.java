package com.mintpop.shop.service;

/**
 * 邮件发送结果：发送器不抛异常，失败信息经本对象上报给调用方入库。
 */
public record MailResult(boolean sent, String error) {

    public static MailResult ok() {
        return new MailResult(true, null);
    }

    public static MailResult failed(String error) {
        return new MailResult(false, error);
    }
}
