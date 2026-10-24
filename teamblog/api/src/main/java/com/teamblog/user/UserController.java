package com.teamblog.user;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户相关端点。
 *
 * <p>{@code /me} 是最小的"受保护端点"：它存在的意义不是取数据（数据本来就在 token 里
 * 能拼一半），而是让前端有一个地方验证"当前会话还有效吗"，也是 1.2 验收
 * "有效 token 200 / 篡改后 401" 的落地目标。
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.getMe(principal.id()));
    }
}
