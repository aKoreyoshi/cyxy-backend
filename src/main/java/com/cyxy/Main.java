package com.cyxy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 @EnableScheduling 是为了 ExchangeRateScheduler 的每日汇率同步。
 * 不单独建调度线程池：一天只跑两次、每次一个 HTTP 请求，默认单线程调度器足够；
 * 真到了要并行跑多个定时任务时再配 TaskScheduler，别一开始就引入线程池配置。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.cyxy.mapper")
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
