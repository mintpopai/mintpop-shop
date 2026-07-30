package com.mintpop.shop.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper 扫描配置：从启动类拆出，避免 @WebMvcTest 等切片测试加载启动类注解时
 * 把 mapper 一并注册（切片上下文无 SqlSessionFactory，会导致启动失败）。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.mintpop.shop.mapper")
public class MyBatisConfig {

    /** 分页插件：管理端列表接口（订单/用户）依赖，不配则 selectPage 静默拉全表 */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
