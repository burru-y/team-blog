package com.teamblog.todo;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 个人待办接口。
 *
 * <p>两类路径刻意分开：
 * <ul>
 *   <li>{@code /api/v1/users/{username}/todos} 公开读——主页给访客看</li>
 *   <li>{@code /api/v1/me/todos} 需要登录——自己的增删改</li>
 * </ul>
 * 这样"谁能写"只由 URL 规则决定，不依赖方法上的注解（项目没开方法级安全）。
 */
@RestController
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping("/api/v1/users/{username}/todos")
    public ApiResponse<List<TodoResponse>> list(@PathVariable String username) {
        return ApiResponse.ok(todoService.listByUsername(username));
    }

    @GetMapping("/api/v1/me/todos")
    public ApiResponse<List<TodoResponse>> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(todoService.listMine(principal.id()));
    }

    @PostMapping("/api/v1/me/todos")
    public ResponseEntity<ApiResponse<TodoResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody TodoService.CreateTodoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(todoService.create(principal.id(), request)));
    }

    @PatchMapping("/api/v1/me/todos/{id}")
    public ApiResponse<TodoResponse> toggle(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID id) {
        return ApiResponse.ok(todoService.toggle(principal.id(), principal.role(), id));
    }

    @DeleteMapping("/api/v1/me/todos/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID id) {
        todoService.delete(principal.id(), principal.role(), id);
        return ApiResponse.ok(null);
    }

    /** 清空自己的已完成条目，返回删除数量。 */
    @DeleteMapping("/api/v1/me/todos")
    public ApiResponse<Integer> clearDone(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(todoService.clearDone(principal.id()));
    }
}
