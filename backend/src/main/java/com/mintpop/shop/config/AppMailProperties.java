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

    /** 发信地址（通常与 spring.mail.username 相同） */
    private String from;
    /** 发信人展示名 */
    private String fromName = "MintPop Shop";
    /** 站点地址，用于拼订单详情页链接 */
    private String siteBaseUrl = "https://mintpop.ai";
}
