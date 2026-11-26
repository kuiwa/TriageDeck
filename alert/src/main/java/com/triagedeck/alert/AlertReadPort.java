package com.triagedeck.alert.api.port;

import com.triagedeck.alert.api.AlertFilter;
import com.triagedeck.alert.api.AlertPageView;
import com.triagedeck.alert.api.AlertView;
import java.util.UUID;

/**
 * Read-only port for alert data access.
 * AI module and other modules can depend on this port only.
 * No write operations allowed - read only.
 */
public interface AlertReadPort {

    /**
     * Find alert by ID.
     * @param id alert UUID
     * @return AlertView if found
     */
    AlertView findById(UUID id);

    /**
     * Find alerts with pagination and filtering (read-only).
     * @param filter filter criteria (severity, source, keyword, date range)
     * @param page page number (0-indexed)
     * @param size page size (max 100)
     * @return AlertPageView with items and total
     */
    AlertPageView findAll(AlertFilter filter, int page, int size);
}