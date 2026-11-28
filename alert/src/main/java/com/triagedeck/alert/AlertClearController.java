package com.triagedeck.alert.api;

import com.triagedeck.alert.service.AlertClearService;
import com.triagedeck.platform.security.RequirePermission;
import com.triagedeck.platform.trace.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class AlertClearController {

    private final AlertClearService clearService;
    private final TraceIds traceIds;

    public AlertClearController(AlertClearService clearService, TraceIds traceIds) {
        this.clearService = clearService;
        this.traceIds = traceIds;
    }

    @PostMapping("/api/alerts/{id}/clear")
    @RequirePermission(obj = "alert", act = "clear")
    public ResponseEntity<Object> clear(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {

        try {
            return ResponseEntity.ok(clearService.clear(id, reason));
        } catch (AlertClearService.AlertAlreadyClearedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AlertApiError("ALREADY_CLEARED", "告警已被清除", traceIds.current()));
        } catch (AlertClearService.AlertNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AlertApiError("ALERT_NOT_FOUND", "告警不存在", traceIds.current()));
        }
    }

    public static class AlertClearResponse {
        private final boolean success;

        public AlertClearResponse(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
