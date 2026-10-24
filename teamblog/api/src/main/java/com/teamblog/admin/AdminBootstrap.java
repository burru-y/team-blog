package com.teamblog.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;

/**
 * 首启管理员引导。
 *
 * <p>解决的问题：新部署的库是空的，而"创建账号"需要管理员邀请 —— 死锁。
 * 之前只能手工执行 SQL，那是"部署文档里的第 7 步"，总会有人漏。
 *
 * <p>三条设计原则：
 * <ol>
 *   <li><b>只在空库时执行</b>：库里只要有一个用户就完全跳过，不做任何修正</li>
 *   <li><b>没有密码就不建</b>：绝不生成默认密码，那等于给公网留一个后门</li>
 *   <li><b>建完必须提醒改密</b>：日志里明确写，因为一期没有"强制改密"功能</li>
 * </ol>
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AdminBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        if (properties.password() == null || properties.password().isBlank()) {
            log.warn("""
                    数据库里没有任何账号，且未设置 ADMIN_BOOTSTRAP_PASSWORD。
                    请设置该环境变量后重启，或用 SQL 手工创建第一个管理员——
                    否则这个实例无法登录，也就无法邀请任何人。""");
            return;
        }

        String username = properties.username();
        User admin = User.create(username, username + "@teamblog.local",
                passwordEncoder.encode(properties.password()), Role.ADMIN);
        userRepository.save(admin);

        log.warn("已创建初始管理员 '{}'。请立即登录并在后续版本中修改密码——"
                + "ADMIN_BOOTSTRAP_PASSWORD 只用于首次引导，之后应从环境变量中移除。", username);
    }
}
