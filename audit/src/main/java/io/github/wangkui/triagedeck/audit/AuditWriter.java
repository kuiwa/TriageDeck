package io.github.wangkui.triagedeck.audit;

public interface AuditWriter {
    void write(AuditCommand command);
}