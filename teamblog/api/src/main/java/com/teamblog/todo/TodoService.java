package com.teamblog.todo;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 个人待办。
 *
 * <p>读是公开的：主页上的清单本来就给别人看（"我最近在做什么"）。
 * 写只有本人或管理员——管理员的权限是为了清理不当内容，不做日常编辑。
 */
@Service
public class TodoService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_LABEL = 120;
    private static final int MAX_ITEMS = 50;

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    public TodoService(TodoRepository todoRepository, UserRepository userRepository) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TodoResponse> listByUsername(String username) {
        User user = userRepository.findActiveByUsername(User.normalizeUsername(username))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return todoRepository.findByUserIdOrderByDoneAscPositionAscCreatedAtAsc(user.getId()).stream()
                .map(TodoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TodoResponse> listMine(UUID userId) {
        return todoRepository.findByUserIdOrderByDoneAscPositionAscCreatedAtAsc(userId).stream()
                .map(TodoResponse::from)
                .toList();
    }

    @Transactional
    public TodoResponse create(UUID userId, CreateTodoRequest request) {
        String label = request.label() == null ? "" : request.label().trim();
        if (label.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "内容不能为空");
        }
        if (label.length() > MAX_LABEL) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "内容最多 " + MAX_LABEL + " 字");
        }
        if (todoRepository.findByUserIdOrderByDoneAscPositionAscCreatedAtAsc(userId).size() >= MAX_ITEMS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "待办最多 " + MAX_ITEMS + " 条");
        }

        Todo todo = Todo.create(userId, label, todoRepository.nextPosition(userId), OffsetDateTime.now(ZONE));
        todoRepository.save(todo);
        return TodoResponse.from(todo);
    }

    @Transactional
    public TodoResponse toggle(UUID actorId, Role actorRole, UUID todoId) {
        Todo todo = requireOwned(actorId, actorRole, todoId);
        todo.toggle(OffsetDateTime.now(ZONE));
        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(UUID actorId, Role actorRole, UUID todoId) {
        Todo todo = requireOwned(actorId, actorRole, todoId);
        todoRepository.delete(todo);
    }

    /** 清空已完成：主页上勾掉一堆之后，一条一条删太麻烦。 */
    @Transactional
    public int clearDone(UUID userId) {
        List<Todo> done = todoRepository.findByUserIdOrderByDoneAscPositionAscCreatedAtAsc(userId).stream()
                .filter(Todo::isDone)
                .toList();
        todoRepository.deleteAll(done);
        return done.size();
    }

    private Todo requireOwned(UUID actorId, Role actorRole, UUID todoId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "待办不存在"));
        if (actorRole != Role.ADMIN && !todo.isOwnedBy(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己的待办");
        }
        return todo;
    }

    /** 新建请求。 */
    public record CreateTodoRequest(String label) {
    }
}
