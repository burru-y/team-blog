package com.teamblog.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储（阿里云 OSS）配置。
 *
 * <p>为什么要有这一层：图片存在服务器本地时，换机器、扩容、迁移都得搬文件，
 * 而且服务器磁盘只有 40G；放在 OSS 后，图片和备份一样与机器解耦。
 *
 * <p>四要素为空时整体降级为「存本地」——本地开发和没配 OSS 的部署仍然能用，
 * 不需要为了跑起来先注册一个云账号。
 *
 * @param endpoint       oss-cn-hangzhou.aliyuncs.com
 * @param bucket         存放用户上传图片的 bucket（需公共读，浏览器直连）
 * @param accessKeyId    RAM 子账号的 AccessKey
 * @param accessKeySecret RAM 子账号的 Secret
 * @param publicBaseUrl  CDN 或 bucket 的对外访问前缀，末尾不带斜杠
 */
@ConfigurationProperties(prefix = "oss")
public record OssProperties(
        String endpoint,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String publicBaseUrl
) {

    /** 四要素齐全才启用；缺一个就退回本地磁盘。 */
    public boolean enabled() {
        return notBlank(endpoint) && notBlank(bucket) && notBlank(accessKeyId)
                && notBlank(accessKeySecret) && notBlank(publicBaseUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
