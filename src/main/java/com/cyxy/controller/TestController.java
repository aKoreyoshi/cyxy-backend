package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.common.ResultCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年10月27日, 22:23:36
 */
@RestController
public class TestController {
    @GetMapping("/api/user")
    public Result<String> user(){
        System.out.println("-----前端发送请求。");
        return Result.ok( "请求成功。");
    }
}