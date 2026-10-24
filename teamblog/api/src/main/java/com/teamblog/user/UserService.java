package com.teamblog.user;

import com.teamblog.common.BusinessException;
import com.teamblog.common.CursorUtils;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.PageResponse;
import org.springframework.data.domain.PageRequest;
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
@Service
public class UserService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

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

        return PageResponse.of(page.stream().map(UserCardResponse::from).toList(), nextCursor);
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

    /** A-08：默认 20，上限 100。超出范围的请求不该报错，静默收敛到合法值。 */
    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
