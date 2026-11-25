package io.github.wangkui.triagedeck.security.api;

public record ApiError(String code, String message, String traceId) {
}
