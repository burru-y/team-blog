package com.teamblog.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * 上传配置。
 *
 * <p>为什么目录要可配：本地是 `./uploads`，容器里是挂载卷，生产由 Nginx 直接对外服务
 * 这个目录（不经 Spring），所以路径必须能在部署时改。
 */
@Validated
@ConfigurationProperties(prefix = "upload")
public record UploadProperties(
        @NotBlank String dir,
        @NotNull DataSize maxImageSize,
        @NotNull DataSize maxCodeSize
) {
}
