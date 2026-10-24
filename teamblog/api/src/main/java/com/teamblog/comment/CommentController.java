package com.teamblog.comment;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.comment.dto.CommentCreateRequest;
import com.teamblog.comment.dto.CommentResponse;
import com.teamblog.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 评论端点。
 *
 * <p>读公开（访客能看讨论），写需要登录；删除的归属校验在 Service（S-01 第三重）。
 */
@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<List<CommentResponse>> list(@RequestParam String targetType,
                                                   @RequestParam UUID targetId) {
        return ApiResponse.ok(commentService.list(targetType, targetId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(commentService.create(principal.id(), request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        commentService.delete(principal.id(), principal.role(), id);
        return ApiResponse.ok(null);
    }
}
