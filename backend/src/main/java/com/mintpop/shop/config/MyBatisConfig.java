package com.mintpop.shop.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper 扫描配置：从启动类拆出，避免 @WebMvcTest 等切片测试加载启动类注解时
 * 把 mapper 一并注册（切片上下文无 SqlSessionFactory，会导致启动失败）。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.mintpop.shop.mapper")
public class MyBatisConfig {
}
