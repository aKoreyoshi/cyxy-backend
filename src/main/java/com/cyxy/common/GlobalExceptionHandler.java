package com.cyxy.common;

import com.cyxy.exception.CYXYException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * @author: Koreyoshi
 * @description: 全局异常处理
 * @version: 1.0
 * @date: 2026年10月27日, 22:59:23
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常：自己主动 throw 的，比如"用户名已存在"
    @ExceptionHandler(CYXYException.class)
    public Result<Void> handleBiz(CYXYException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    // multipart 请求在「进入 Controller 之前」就由 Spring 解析并校验大小，
    // 所以 CosUploadService 里那道 10MB 判断根本没机会执行 —— 超限会直接抛到这里。
    // 不加这个分支就会落到下面的兜底，前端弹「服务器开小差了：Maximum upload size exceeded」，
    // 用户完全看不懂，还以为是系统崩了。这里统一翻译成人话，Excel 批量导入也共用这条提示。
    // 注意：@ExceptionHandler 按「最具体的异常类型」匹配，所以本方法会优先于兜底生效。
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return Result.fail(400, "上传文件不能超过 10MB，请压缩后重试");
    }

    // 兜底：没预料到的异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.fail(500, "服务器开小差了：" + e.getMessage());
    }
}