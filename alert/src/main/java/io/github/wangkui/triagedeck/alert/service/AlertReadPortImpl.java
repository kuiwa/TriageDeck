package io.github.wangkui.triagedeck.alert.service;

import io.github.wangkui.triagedeck.alert.api.AlertFilter;
import io.github.wangkui.triagedeck.alert.api.AlertPageView;
import io.github.wangkui.triagedeck.alert.api.AlertView;
import io.github.wangkui.triagedeck.alert.api.port.AlertReadPort;
import io.github.wangkui.triagedeck.alert.repo.AlertActiveRepository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * {@link AlertReadPort} 的唯一实现：把只读查询映射到 alerts_active。
 * 仅暴露读能力，供 ai / audit 等模块使用。
 */
@Service
public class AlertReadPortImpl implements AlertReadPort {

    private static final int MAX_PAGE_SIZE = 100;

    private final AlertActiveRepository repository;

    public AlertReadPortImpl(AlertActiveRepository repository) {
        this.repository = repository;
    }

    @Override
    public AlertView findById(UUID id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public AlertPageView findAll(AlertFilter filter, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter != null) {
            if (filter.severity() != null) {
                params.addValue("severity", filter.severity().name());
            }
            if (filter.source() != null && !filter.source().isBlank()) {
                params.addValue("source", filter.source());
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                params.addValue("keyword", filter.keyword());
            }
            if (filter.occurredAtFrom() != null) {
                params.addValue("from", filter.occurredAtFrom().toString());
            }
            if (filter.occurredAtTo() != null) {
                params.addValue("to", filter.occurredAtTo().toString());
            }
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        AlertActiveRepository.Page<AlertView> data = repository.findActive(params, safePage, safeSize);
        return new AlertPageView(data.getItems(), data.getTotal(), data.getPage(), data.getSize());
    }
}
