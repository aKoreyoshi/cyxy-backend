package com.cyxy.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author: Koreyoshi
 * @description:
 * 注册登录鉴权拦截器。
 * 原来这个类是个空壳（没有 @Configuration、也没实现 WebMvcConfigurer），
 * Spring 根本不会加载它 —— 这就是现在所有 /api 接口裸奔的原因。
 * @version: 1.0
 * @date: 2026年10月31日, 00:32:20
 */

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 登录接口本身不能拦，否则永远拿不到 token，形成死锁
                .excludePathPatterns("/api/login");
    }
}