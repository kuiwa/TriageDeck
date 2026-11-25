package io.github.wangkui.triagedeck.alert.domain;

public final class AlertIngestValidationException extends RuntimeException {

    public AlertIngestValidationException() {
        super("告警请求不符合字段约束");
    }
}
