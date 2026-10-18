package com.adgn.backend.core.idempotency;
import java.util.UUID;
import java.util.Objects;
public record IdempotencyRequest(UUID principalId,String operationId,String method,String path,UUID key,byte[] canonicalBody) {
    public IdempotencyRequest {
        Objects.requireNonNull(principalId); Objects.requireNonNull(key); Objects.requireNonNull(canonicalBody);
        if (operationId == null || !operationId.matches("[A-Za-z][A-Za-z0-9_]{0,99}")
                || method == null || !method.matches("POST|PUT|PATCH|DELETE")
                || path == null || !path.startsWith("/api/v1/") || path.length()>300
                || path.contains("?") || path.contains("#") || path.contains("\\")
                || path.contains("//") || path.contains("%") || path.contains("/../") || path.endsWith("/..")
                || path.contains("/./") || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid internal idempotency metadata");
        }
        canonicalBody=canonicalBody.clone();
    }
    @Override public byte[] canonicalBody() { return canonicalBody.clone(); }
    @Override public String toString() { return "IdempotencyRequest[redacted]"; }
}
