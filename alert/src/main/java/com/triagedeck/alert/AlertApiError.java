package com.triagedeck.alert.api;

public record AlertApiError(String code, String message, String traceId) {
}
