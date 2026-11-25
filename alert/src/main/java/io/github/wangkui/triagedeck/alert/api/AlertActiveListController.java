package io.github.wangkui.triagedeck.alert.api;

import io.github.wangkui.triagedeck.alert.repo.AlertActiveRepository;
import io.github.wangkui.triagedeck.alert.repo.AlertActiveRepository.Page;
import io.github.wangkui.triagedeck.platform.security.RequirePermission;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
public class AlertActiveListController {

    private final AlertActiveRepository repository;

    public AlertActiveListController(AlertActiveRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/alerts/active")
    @RequirePermission(obj = "alert", act = "read")
    public ResponseEntity<AlertPageView> active(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword) {

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (severity != null) {
            params.addValue("severity", severity);
        }
        if (source != null) {
            params.addValue("source", source);
        }
        if (from != null) {
            params.addValue("from", from);
        }
        if (to != null) {
            params.addValue("to", to);
        }
        if (keyword != null) {
            params.addValue("keyword", "%" + keyword + "%");
        }

        Page<AlertView> data = repository.findActive(params, page, size);

        AlertPageView view = new AlertPageView(
                data.getItems(),
                data.getTotal(),
                data.getPage(),
                data.getSize());

        return ResponseEntity.ok(view);
    }

    public static class AlertPageView {
        private final List<AlertView> items;
        private final long total;
        private final int page;
        private final int size;

        public AlertPageView(List<AlertView> items, long total, int page, int size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }

        public List<AlertView> getItems() { return items; }
        public long getTotal() { return total; }
        public int getPage() { return page; }
        public int getSize() { return size; }
    }
}