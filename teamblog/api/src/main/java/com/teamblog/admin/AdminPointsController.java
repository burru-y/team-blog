package com.teamblog.admin;

import com.teamblog.common.ApiResponse;
import com.teamblog.points.PointReconciliationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维端点：手动触发积分对账。
 *
 * <p>为什么要有它：定时任务是"看不见的"，出问题时需要一个能立刻验证的入口；
 * 上线初期也可以手动跑一次确认账本与总分一致，而不是等到凌晨。
 *
 * <p>路径在 {@code /api/v1/admin/**} 下，安全配置已经限制为 ADMIN（S-02）。
 */
@RestController
@RequestMapping("/api/v1/admin/points")
public class AdminPointsController {

    private final PointReconciliationService reconciliationService;

    public AdminPointsController(PointReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/reconcile")
    public ApiResponse<PointReconciliationService.ReconciliationReport> reconcile() {
        return ApiResponse.ok(reconciliationService.reconcile());
    }
}
