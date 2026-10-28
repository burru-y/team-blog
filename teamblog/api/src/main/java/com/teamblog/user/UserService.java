package com.teamblog.user;

import com.teamblog.common.BusinessException;
import com.teamblog.common.CursorUtils;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.PasswordRules;
import com.teamblog.user.dto.UpdateProfileRequest;
import com.teamblog.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户读取服务。当前只有"我是谁"，后续的公开资料查询也放这里。
 *
 * <p>为什么 Controller 不直接调 Repository：ArchUnit 规则会拦（J-02/§13），更实际的理由
 * 是"软删账号不算存在"这类判断必须集中，散在 Controller 里早晚漏一处。
 */
@Slf4j
@Service
public class UserService {

    /**
     * 引导账号：不对公开展示。
     *
     * <p>它是部署时为了进后台而建的，没有个人资料，出现在成员列表里只会让人困惑。
     */
    private static final String BOOTSTRAP_USERNAME = "admin1";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * 成员列表（公开）。
     *
     * <p>多取一条来判断"还有没有下一页"：比再发一次 count 查询便宜，也不会因为
     * 两次查询之间有人插入而算错。
     */
    @Transactional(readOnly = true)
    public PageResponse<UserCardResponse> listMembers(String cursor, int limit) {
        int size = normalizeLimit(limit);

        List<User> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = userRepository.findFirstPage(PageRequest.of(0, size + 1));
        } else {
            CursorUtils.Cursor decoded = CursorUtils.decode(cursor);
            rows = userRepository.findPageAfter(decoded.createdAt(), decoded.id(), PageRequest.of(0, size + 1));
        }

        boolean hasNext = rows.size() > size;
        List<User> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext
                ? CursorUtils.encode(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getId())
                : null;

        // 引导账号不在公开列表里出现
        List<UserCardResponse> cards = page.stream()
                .filter(user -> !BOOTSTRAP_USERNAME.equals(user.getUsername()))
                .map(UserCardResponse::from)
                .toList();

        return PageResponse.of(cards, nextCursor);
    }

    /**
     * 公开资料（访客可看）。
     *
     * <p>邮箱是否返回由 {@code email_public} 决定（S-10）——这条规则写在 DTO 的
     * {@code fromPublic} 里，接口层不需要知道细节。
     */
    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(String username) {
        return userRepository.findActiveByUsername(User.normalizeUsername(username))
                .map(UserResponse::fromPublic)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    /**
     * 修改本人资料。
     *
     * <p>只覆盖传入的字段，未传的保持原值。这样前端可以做"只改简介"这类局部提交，
     * 不会因为漏传某个字段把头像清空。
     */
    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        String avatarUrl = request.avatarUrl() != null ? blankToNull(request.avatarUrl()) : user.getAvatarUrl();
        String bio = request.bio() != null ? blankToNull(request.bio()) : user.getBio();
        String githubUrl = request.githubUrl() != null ? blankToNull(request.githubUrl()) : user.getGithubUrl();
        String blogUrl = request.blogUrl() != null ? blankToNull(request.blogUrl()) : user.getBlogUrl();
        String accentColor = request.accentColor() != null
                ? blankToNull(request.accentColor())
                : user.getAccentColor();
        boolean emailPublic = request.emailPublic() != null ? request.emailPublic() : user.isEmailPublic();

        String displayName = request.displayName() != null
                ? blankToNull(request.displayName())
                : user.getDisplayName();

        // 改名要单独处理：归一化之后比对唯一性，同名视为无操作
        String newUsername = blankToNull(request.username());
        if (newUsername != null) {
            String normalized = User.normalizeUsername(newUsername);
            if (!normalized.equals(user.getUsername())) {
                if (userRepository.existsByUsername(normalized)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
                }
                user.rename(normalized);
            }
        }

        user.updateProfile(avatarUrl, bio, githubUrl, blogUrl, accentColor, emailPublic, displayName);
        return user;
    }

    /** 空串按"清空"处理，避免前端提交空字符串把字段存成 "" 而不是 NULL。 */
    private static String blankToNull(String raw) {
        String trimmed = raw == null ? null : raw.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 管理员重置他人密码。
     *
     * <p>一期没有邮件服务，不能做"忘记密码"，所以唯一的恢复路径就是管理员重置。
     * 改完 tokenVersion 会 +1，对方已登录的设备立刻失效——这既是安全要求，
     * 也是"确实重置了"的证明。
     */
    @Transactional
    public void resetPassword(UUID actorId, UUID targetId, String newPassword) {
        User user = userRepository.findById(targetId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        PasswordRules.validate(newPassword, user.getUsername());
        user.changePassword(passwordEncoder.encode(newPassword));

        log.warn("管理员 {} 重置了用户 {} 的密码（旧会话已失效）", actorId, user.getUsername());
    }

    /**
     * 修改自己的密码。
     *
     * <p>必须验证旧密码：否则一个被盗用的 access token 就能改掉密码、彻底接管账号。
     */
    @Transactional
    public void changeOwnPassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            // 与登录失败同样的文案：不额外透露"密码是否正确"
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }

        PasswordRules.validate(newPassword, user.getUsername());
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    /** A-08：默认 20，上限 100。超出范围的请求不该报错，静默收敛到合法值。 */
    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
