package com.teamblog.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * 调 AI 服务的内部摘要端点。
 *
 * <p>为什么要 HMAC 而不是内网信任：AI 服务被 Nginx 暴露在公网路由下，
 * 虽然 Nginx 挡住了 `/ai/v1/summarize`，但"只有一层挡着"不够——容器网络里任何东西
 * 都能直接访问 ai:8000。请求签名让 AI 服务自己也能判断调用方是不是 Spring。
 *
 * <p>签名对象是**原始正文字节**（不是 JSON 字符串再序列化一次）：
 * 两边各序列化一次，字段顺序/空格差异会让签名对不上。
 */
public class AiSummaryClient {

    private final RestClient restClient;
    private final String hmacSecret;

    public AiSummaryClient(AiProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(clientRequestFactory(properties.timeout()))
                .build();
        this.hmacSecret = properties.hmacSecret();
    }

    /**
     * @return 摘要文本
     * @throws AiUnavailableException AI 服务不可用或返回非 2xx
     */
    public String summarize(String content) {
        String signature = sign(content);
        try {
            SummaryResponse response = restClient.post()
                    .uri("/v1/summarize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Signature", signature)
                    .body(Map.of("content", content))
                    .retrieve()
                    .body(SummaryResponse.class);

            if (response == null || response.summary() == null || response.summary().isBlank()) {
                throw new AiUnavailableException("AI 返回了空摘要");
            }
            return response.summary();
        } catch (RestClientException ex) {
            throw new AiUnavailableException("调用 AI 失败：" + ex.getMessage(), ex);
        }
    }

    /** HmacSHA256(secret, content) 的小写 hex，与 Python 侧 hmac.new(...).hexdigest() 一致。 */
    String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory(Duration timeout) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    public record SummaryResponse(String summary) {
    }

    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }

        public AiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
