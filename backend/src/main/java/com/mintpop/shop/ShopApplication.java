package com.mintpop.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/**
 * MintPop 商店后端启动类。mapper 扫描见 MyBatisConfig（拆出以保持测试切片干净）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopApplication {

    public static void main(String[] args) {
        // 全链路 UTC：JVM 默认时区钉死为 UTC，LocalDateTime.now()/日志等不再随部署环境时区漂移；
        // 数据库侧由 JDBC 连接参数把会话时区同样强制为 UTC（见 config/application.yml），两端同源
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ShopApplication.class, args);
    }
}
