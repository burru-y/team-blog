package com.teamblog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 冒烟测试：只验证 Spring 上下文能起来、Flyway 能在测试库上跑通。
 *
 * 用 test profile 而不是 dev：原因见 src/test/resources/application-test.yml。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
