package io.github.wangkui.triagedeck.alert.api;

import io.github.wangkui.triagedeck.alert.service.AlertClearService;
import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class AlertClearController {

    private final AlertClearService clearService;

    public AlertClearController(AlertClearService clearService) {
        this.clearService = clearService;
    }

    @PostMapping("/api/alerts/{id}/clear")
    @RequirePermission(obj = "alert", act = "clear")
    public ResponseEntity<AlertClearResponse> clear(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {

        try {
            AlertClearResponse response = clearService.clear(id, reason);
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(response);
            }
        } catch (AlertClearService.AlertNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AlertClearResponse(false));
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