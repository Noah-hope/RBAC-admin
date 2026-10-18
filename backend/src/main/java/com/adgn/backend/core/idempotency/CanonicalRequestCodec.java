package com.adgn.backend.core.idempotency;
/** Domain adapter must encode all semantic body/query/version fields; not ordinary DTO JSON or a JCS implementation. */
@FunctionalInterface
public interface CanonicalRequestCodec<T> { byte[] encode(T semanticRequest); }
