package com.teamblog.auth;

import com.teamblog.admin.AdminInvite;
import com.teamblog.admin.AdminInviteService;
import com.teamblog.admin.dto.InviteInfoResponse;
import com.teamblog.auth.dto.RegisterRequest;
import com.teamblog.common.ApiResponse;
import com.teamblog.user.User;
import com.teamblog.user.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 凭邀请注册（公开端点，路径在 `/api/v1/invites/**`）。
 *
 * <p>为什么分成"先查"和"再提交"两步：注册页需要预先展示"这个链接是给谁的"，
 * 用户才敢填表单；如果只有提交一步，用户填完密码才被告知"链接已失效"，体验很差。
 *
 * <p>为什么放在 auth 包而不是 admin 包：这是**注册**流程，只是凭证来自邀请；
 * 管理员那一侧（生成邀请）才是 admin 的职责。
 */
@RestController
@RequestMapping("/api/v1/invites")
public class RegistrationController {

    private final AdminInviteService inviteService;

    public RegistrationController(AdminInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/{token}")
    public ApiResponse<InviteInfoResponse> info(@PathVariable String token) {
        AdminInvite invite = inviteService.requireUsable(token);
        return ApiResponse.ok(new InviteInfoResponse(
                invite.getEmail(), invite.getRole().name(), invite.getExpiresAt()));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<UserResponse>> accept(@PathVariable String token,
                                                            @Valid @RequestBody RegisterRequest request) {
        User user = inviteService.accept(token, request.username(), request.password(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(UserResponse.from(user)));
    }
}
