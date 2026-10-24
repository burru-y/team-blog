package com.teamblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务（refresh token 清理）。
 *
 * <p>为什么单独一个配置类而不是加在启动类上：启动类只该做"启动"，把 @EnableScheduling
 * 这类能力开关放在这里，看配置就知道系统里有哪些后台任务。
 *
 * <p>单实例部署下用 Spring 自带调度足够；多实例时同一个任务会被每个实例各跑一遍——
 * 那时要换成分布式锁或外部调度器（2C4G 单机，当前不需要）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
