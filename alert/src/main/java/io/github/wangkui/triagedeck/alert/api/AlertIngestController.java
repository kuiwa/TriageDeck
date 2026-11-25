package io.github.wangkui.triagedeck.alert.api;

import io.github.wangkui.triagedeck.alert.domain.Ingress;
import io.github.wangkui.triagedeck.alert.service.AlertIngestService;
import io.github.wangkui.triagedeck.platform.security.RequirePermission;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertIngestController {

    private final AlertIngestService ingestService;

    public AlertIngestController(AlertIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/api/alerts/ingest")
    @RequirePermission(obj = "alert", act = "ingest")
    public ResponseEntity<AlertIngestReceipt> ingest(
            @Valid @RequestBody AlertIngestRequest request) {
        AlertIngestReceipt receipt =
                AlertIngestReceipt.from(ingestService.ingest(request.toCommand(), Ingress.HTTP));
        return ResponseEntity.accepted().body(receipt);
    }
}
