package com.triagedeck.platform.trace;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TraceIds {

    private static final ThreadLocal<String> current = ThreadLocal.withInitial(() -> UUID.randomUUID().toString());

    public String current() {
        return current.get();
    }

    public void set(String traceId) {
        current.set(traceId);
    }

    public void clear() {
        current.remove();
    }
}