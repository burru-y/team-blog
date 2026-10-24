package com.teamblog.health;

import com.teamblog.common.ApiResponse;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：容器与 uptime 探活都打这个端点。
 * 必须同时验证「数据库可连」与「迁移版本」，否则会出现应用起来了但表还没建完的假活。
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final Flyway flyway;

    public HealthController(JdbcTemplate jdbcTemplate, Flyway flyway) {
        this.jdbcTemplate = jdbcTemplate;
        this.flyway = flyway;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        jdbcTemplate.queryForObject("select 1", Integer.class);

        var current = flyway.info().current();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("db", "UP");
        body.put("flyway", current == null ? "NONE" : current.getVersion().getVersion());
        return ApiResponse.ok(body);
    }
}
