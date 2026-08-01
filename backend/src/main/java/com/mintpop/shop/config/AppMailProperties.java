package com.mintpop.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 发货邮件的自有配置（app.mail.*）。SMTP 主机/账号/口令走标准 spring.mail.*，
 * 二者都写在 jar 外 config/application.yml，不入库不进仓库。
 */
@Data
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    /** 发信地址（通常与 spring.mail.username 相同）。留空 = 邮件未配置，等同于没配 spring.mail.host */
    private String from;
    /** 发信人展示名 */
    private String fromName = "MintPop Shop";
    /** 站点地址，用于拼订单详情页链接 */
    private String siteBaseUrl = "https://mintpop.ai";
    /**
     * 买家未设置语言偏好（{@code shop_user.locale} 为空）时的发信语言兜底，BCP47 标签。
     * 不能用发货请求的当前语言兜底：发货是管理员在管理端发起的请求，管理端固定用 zh-CN
     * 调后端（管理端不做双语），那与买家语言毫无关系，会让英文买家收到中文邮件。
     */
    private String defaultLocale = "zh-CN";
}
