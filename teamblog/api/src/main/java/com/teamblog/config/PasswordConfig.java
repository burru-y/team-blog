package com.teamblog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希的唯一入口。
 *
 * <p>为什么单独一个配置类而不是塞进 SecurityConfig：SecurityConfig 负责"谁能访问哪个
 * 路径"，这里负责"密码怎么存"，两件事的变更频率和责任人不同（J-25 按角色拆类）。
 *
 * <p>强度固定 12（S-03）：10 在现代 GPU 上偏快，14 会让登录接口成为 CPU 瓶颈。
 * 单次约 250ms，这正是需要它的地方——攻击者拿库后同样要付这个成本。
 */
@Configuration
public class PasswordConfig {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
