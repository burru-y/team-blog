package com.teamblog.checkin;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.checkin.dto.CheckinResult;
import com.teamblog.checkin.dto.CheckinStatus;
import com.teamblog.checkin.dto.HeatmapResponse;
import com.teamblog.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 打卡端点。
 *
 * <p>打卡需要登录；热力图公开（首页要展示团队活跃度）。
 */
@RestController
@RequestMapping("/api/v1")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    /**
     * 打卡。
     *
     * <p>成功返回 201；今天已经打过返回 409 {@code ALREADY_CHECKED_IN}——
     * 这是**正常的幂等拒绝**，前端应该把它当作"按钮置灰"而不是报错红条。
     */
    @PostMapping("/checkins")
    public ResponseEntity<ApiResponse<CheckinResult>> checkin(@AuthenticationPrincipal AuthPrincipal principal,
                                                              @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(checkinService.checkin(principal.id(), note)));
    }

    @GetMapping("/checkins/status")
    public ApiResponse<CheckinStatus> status(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(checkinService.status(principal.id()));
    }

    /** 热力图：不给 username 就是团队视角，给了就是个人视角。 */
    @GetMapping("/stats/heatmap")
    public ApiResponse<HeatmapResponse> heatmap(@RequestParam(required = false) String username,
                                                @RequestParam(defaultValue = "120") int days) {
        return ApiResponse.ok(checkinService.heatmap(username, Math.min(Math.max(days, 7), 365)));
    }
}
