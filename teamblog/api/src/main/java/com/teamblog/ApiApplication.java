package com.teamblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用入口。
 *
 * <p>{@code @ConfigurationPropertiesScan}：让 {@code @ConfigurationProperties} 类不需要
 * 逐个 {@code @EnableConfigurationProperties}。少了它，配置类不会被绑定，运行时报
 * "找不到 JwtProperties 这个 bean"。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

}
