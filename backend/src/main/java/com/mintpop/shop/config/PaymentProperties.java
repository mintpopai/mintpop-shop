package com.mintpop.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe 支付配置（payment.stripe.*）：密钥来自 jar 外 config/application.yml，不入库不进仓库。
 */
@Data
@ConfigurationProperties(prefix = "payment.stripe")
public class PaymentProperties {

    /** Stripe secret key（敏感，sk_ 开头） */
    private String secretKey;
    /** Stripe webhook 签名密钥（敏感，whsec_ 开头） */
    private String webhookSecret;
    /** Stripe publishable key（前端初始化 Stripe.js 用，非敏感） */
    private String publishableKey;
    /** 结算币种（3 位 ISO） */
    private String currency = "USD";
    /** 启用的支付子方式，逗号分隔（我方命名：card/alipay/wxpay） */
    private String supportedTypes = "card,alipay,wxpay";
    /** 卡账单描述符后缀（拼在账户缩短描述符后显示为 MINTPOP* <后缀>，区分子业务）；留空则不传 */
    private String statementDescriptorSuffix = "SHOP";
    /** 业务线标记：写入 PaymentIntent 的 Metadata["product"]，webhook 据此认领本业务事件
     * （Stripe 事件是账户级广播，同账户多业务时别家事件靠它静默跳过） */
    private String productCode = "shop";

    /** 支付功能是否已配置可用（secret key 与 publishable key 都有值才算） */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank()
                && publishableKey != null && !publishableKey.isBlank();
    }
}
