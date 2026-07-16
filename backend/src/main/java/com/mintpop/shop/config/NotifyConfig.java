package com.mintpop.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 通知装配：启用 @Async（通知在独立线程池执行，不阻塞支付主流程）+
 * 飞书 webhook 专用 RestClient。拆出独立配置类以保持测试切片干净（与 MyBatisConfig 同理）。
 */
@Configuration
@EnableAsync
public class NotifyConfig {

    /** 连接/读取各 5s 短超时：通知是尽力而为，绝不许占住异步线程 */
    @Bean
    public RestClient feishuRestClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
