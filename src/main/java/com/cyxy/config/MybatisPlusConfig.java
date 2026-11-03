package com.cyxy.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: Koreyoshi
 * @description: MyBatis-Plus 配置类
 * @version: 1.0
 * @date: 2026年10月31日, 01:28:00
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册插件拦截器。当前只启用分页插件，MyBatis-Plus 3.5.9+ 起插件为按需引入，
     * 分页本身不依赖 jsqlparser，故此处无需额外依赖
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}