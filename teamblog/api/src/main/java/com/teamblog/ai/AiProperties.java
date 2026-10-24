package com.teamblog.ai;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 调用 AI 服务的配置。
 *
 * <p>环境变量名刻意和 AI 服务自己的 `AI_BASE_URL` 区分开：那个是"模型地址"，
 * 这个是"AI 服务地址"。两者混用会让容器里配错却毫无提示。
 */
@Validated
@ConfigurationProperties(prefix = "ai.summarizer")
public record AiProperties(
        @NotBlank String url,
        @NotBlank String hmacSecret,
        Duration timeout
) {
}