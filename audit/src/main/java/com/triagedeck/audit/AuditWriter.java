package com.triagedeck.audit;

public interface AuditWriter {
    void write(AuditCommand command);
}