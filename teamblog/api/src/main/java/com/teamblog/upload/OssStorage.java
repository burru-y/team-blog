package com.teamblog.upload;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.ByteArrayInputStream;

/**
 * 阿里云 OSS 存储。
 *
 * <p>只在 {@link OssProperties#enabled()} 为真时生效，否则所有方法返回 null，
 * 调用方退回本地磁盘——这样本地开发不需要任何云凭证。
 *
 * <p>OSS 客户端内部维护连接池，创建一次复用，不要每次上传都 new。
 */
@Component
public class OssStorage {

    private static final Logger log = LoggerFactory.getLogger(OssStorage.class);

    private final OssProperties properties;
    private final OSS client;

    public OssStorage(OssProperties properties) {
        this.properties = properties;
        this.client = properties.enabled()
                ? new OSSClientBuilder().build(
                        properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret())
                : null;
        if (enabled()) {
            log.info("图片将存储到 OSS：bucket={} endpoint={}", properties.bucket(), properties.endpoint());
        } else {
            log.info("未配置 OSS，图片存本地目录");
        }
    }

    public boolean enabled() {
        return client != null;
    }

    /**
     * 上传一个对象并返回可公开访问的 URL。
     *
     * @param key         对象键，如 uploads/2026/xxx.png
     * @param bytes       内容
     * @param contentType 由文件头推断出的类型，不用客户端声明
     * @return 公开 URL；未启用 OSS 时返回 null
     */
    public String put(String key, byte[] bytes, String contentType) {
        if (client == null) {
            return null;
        }
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(bytes.length);
            meta.setContentType(contentType);
            // 图片内容不变即 URL 不变时浏览器可以长期缓存；文件名是 UUID，天然不会重复
            meta.setCacheControl("public, max-age=31536000, immutable");
            client.putObject(properties.bucket(), key, new ByteArrayInputStream(bytes), meta);
            return properties.publicBaseUrl() + "/" + key;
        } catch (RuntimeException ex) {
            // 上传失败不该让"发笔记"整个失败：调用方会退回本地磁盘
            log.warn("上传到 OSS 失败，退回本地存储：{}", ex.getMessage());
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
