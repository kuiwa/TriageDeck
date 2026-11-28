package com.triagedeck.platform.audit;

public interface AccessDeniedAuditPort {
    void recordDenied(String actor, String obj, String act, String path, String method, String clientNet, String traceId);
}