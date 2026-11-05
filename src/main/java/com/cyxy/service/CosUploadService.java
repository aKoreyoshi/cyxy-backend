package com.cyxy.service;

import com.cyxy.exception.CYXYException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * @author: Koreyoshi
 * @description: 产品主图上传：浏览器 → 本服务 → COS（同地域内网）→ 返回公网 URL。
 * 密钥只在后端，前端不接触任何云凭证。
 * @version: 1.0
 * @date: 2026年11月05日, 16:18:29
 */

@Slf4j
@Service
public class CosUploadService {

    // 为什么给默认空串：application-local.yml 不进 git，服务器上漏放这份文件时，
    // @Value 解析不到会直接启动失败，连登录都进不去。给空串让启动照常，
    // 等真正调用上传接口时再报「COS 未配置」，把故障圈在图片功能内部。
    @Value("${cyxy.cos.secret-id:}")
    private String secretId;
    @Value("${cyxy.cos.secret-key:}")
    private String secretKey;
    @Value("${cyxy.cos.region:ap-shanghai}")
    private String region;
    @Value("${cyxy.cos.bucket-name:cyxy-product-1317558205}")
    private String bucketName;

    // 后端必须自己限大小，不能只信前端的 10MB 校验（curl / Postman 能绕过前端）
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Map<String, String> EXT_BY_TYPE = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");

    // 官方要求全局单例（内部有连接池，多实例会耗尽资源）；但又不能在启动期就构造，
    // 因为密钥可能没配。用 volatile + 双重检查做懒加载单例：首次上传才建，之后复用。
    private volatile COSClient client;

    private COSClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    // 抛 CYXYException 而不是让 SDK 报认证错：GlobalExceptionHandler 会包成
                    // code:500 + HTTP 200，前端 request.js 才能正常弹出可读提示。
                    if (!StringUtils.hasText(secretId) || !StringUtils.hasText(secretKey)) {
                        throw new CYXYException("COS 密钥未配置，请联系管理员");
                    }
                    COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
                    // 只给 Region，不要手工 setEndPoint 成外网域名 —— 同地域轻量服务器
                    // 与 COS 默认内网互通，上传不产生流量费，也不吃公网带宽。
                    client = new COSClient(cred, new ClientConfig(new Region(region)));
                }
            }
        }
        return client;
    }

    /**
     * 应用关闭时释放连接池，官方方法名是 shutdown（不是 close）
     */
    @PreDestroy
    public void shutdown() {
        if (client != null) client.shutdown();
    }

    /**
     * 上传一张主图，返回可直接存进 main_image 的 URL
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new CYXYException("请选择图片");
        if (file.getSize() > MAX_SIZE) throw new CYXYException("图片不能超过 10MB");

        // 扩展名从 Content-Type 推，不信文件名后缀（后缀可随便改）
        String type = file.getContentType();
        String ext = type == null ? null : EXT_BY_TYPE.get(type);
        if (ext == null) throw new CYXYException("仅支持 jpg / png / webp / gif 格式");

        byte[] bytes;
        try {
            // 上限 10MB，读进内存换代码简单，省掉 mark/reset 那套流操作
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new CYXYException("图片读取失败: " + e.getMessage());
        }

        // Content-Type 可以伪造。公有读桶里若被塞进 HTML/JS，别人直接访问 URL 有风险，
        // 所以再看一眼文件头魔数，确认它真的是图片。
        if (!magicMatches(type, bytes)) throw new CYXYException("文件内容不是合法图片");

        // key 不含 product_no：传图时往往还没填编号，编号也可能带中文/斜杠，
        // 拼进 URL 还得处理编码。月份 + UUID 纯 ASCII，天然不撞名。
        String key = "product/" + LocalDate.now().format(MONTH) + "/" +
                UUID.randomUUID().toString().replace("-", "") + "." + ext;

        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        meta.setContentType(type);   // 不设的话 COS 当二进制流，<img> 可能不渲染

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            client().putObject(new PutObjectRequest(bucketName, key, in, meta));
        } catch (CYXYException e) {
            throw e;   // 「密钥未配置」是业务提示，原样透出，别打成上传失败
        } catch (Exception e) {
            // SDK 异常栈里含桶名/请求细节，不返回给前端，只落日志
            log.error("COS 上传失败, key={}", key, e);
            throw new CYXYException("图片上传失败，请稍后重试");
        }

        String url = String.format("https://%s.cos.%s.myqcloud.com/%s", bucketName, region, key);
        log.info("COS 上传成功, key={}, url={}", key, url);  // 方便拿去 curl -I 验证响应头
        return url;
    }

    /** 常见图片格式的文件头特征，够挡住「改后缀的假图片」 */
    private boolean magicMatches(String type, byte[] b) {
        if (b == null || b.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            case "image/png"  -> (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
            case "image/gif"  -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
            case "image/webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}