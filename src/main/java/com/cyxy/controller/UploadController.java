package com.cyxy.controller;

import com.cyxy.common.Result;
import com.cyxy.service.CosUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * @author: Koreyoshi
 * @description:
 * @version: 1.0
 * @date: 2026年11月05日, 16:31:49
 */

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final CosUploadService cosUploadService;

    // 路径写全 /api：Vite 代理没有 rewrite，接口必须自带 /api 前缀。
    // 返回 Map 而不是 VO：项目全局 Jackson SNAKE_CASE 会把 VO 的 imageUrl 改成
    // image_url，Map 的 key 不受命名策略影响，前端稳定拿到 { url }。
    @PostMapping("/api/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(Map.of("url", cosUploadService.upload(file)));
    }

}