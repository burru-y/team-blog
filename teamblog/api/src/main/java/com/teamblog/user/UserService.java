package com.teamblog.user;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 用户读取服务。当前只有"我是谁"，后续的公开资料查询也放这里。
 *
 * <p>为什么 Controller 不直接调 Repository：ArchUnit 规则会拦（J-02/§13），更实际的理由
 * 是"软删账号不算存在"这类判断必须集中，散在 Controller 里早晚漏一处。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 取当前登录用户。
     *
     * <p>token 有效但用户查不到，说明账号在 token 有效期内被硬删了——对外仍按"未登录"
     * 处理，不给"这个 id 存在过"的信息。
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
