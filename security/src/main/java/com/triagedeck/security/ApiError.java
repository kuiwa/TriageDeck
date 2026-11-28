package com.triagedeck.security.api;

public record ApiError(String code, String message, String traceId) {
}
