package com.triagedeck.alert.api;

import com.triagedeck.alert.service.AlertDetailService;
import com.triagedeck.platform.security.RequirePermission;
import com.triagedeck.platform.trace.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 告警详情接口（openapi #3 {@code GET /api/alerts/{id}}）。
 * 活动/历史通用，权限 {@code alert:read}。
 */
@RestController
public class AlertDetailController {

    private final AlertDetailService detailService;
    private final TraceIds traceIds;

    public AlertDetailController(AlertDetailService detailService, TraceIds traceIds) {
        this.detailService = detailService;
        this.traceIds = traceIds;
    }

    @GetMapping("/api/alerts/{id}")
    @RequirePermission(obj = "alert", act = "read")
    public ResponseEntity<Object> detail(@PathVariable UUID id) {
        var found = detailService.findDetail(id);
        if (found.isPresent()) {
            return ResponseEntity.ok(found.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AlertApiError("ALERT_NOT_FOUND", "告警不存在", traceIds.current()));
    }
}