package com.adgn.backend;

import com.adgn.backend.core.idempotency.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IdempotencyValueTest {
    private final UUID principal = UUID.randomUUID(), key = UUID.randomUUID();
    private IdempotencyRequest request(String operation, String method, String path, String body) {
        return new IdempotencyRequest(principal, operation, method, path, key, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private byte[] secret() { byte[] bytes=new byte[32]; new java.security.SecureRandom().nextBytes(bytes); return bytes; }
    @Test void digestBindsOperationMethodPathAndCanonicalContentAndDoesNotStorePlaintext() {
        var ring=new HmacKeyRing("v1", Map.of("v1",secret()));
        var original=request("update", "POST", "/api/v1/a", "private-fixture");
        byte[] digest=ring.digest("v1",original);
        assertThat(digest).hasSize(32).isEqualTo(ring.digest("v1",original));
        assertThat(digest).isNotEqualTo(ring.digest("v1",request("update","POST","/api/v1/a","changed")));
        assertThat(digest).isNotEqualTo(ring.digest("v1",request("other","POST","/api/v1/a","private-fixture")));
        assertThat(digest).isNotEqualTo(ring.digest("v1",request("update","PUT","/api/v1/a","private-fixture")));
        assertThat(digest).isNotEqualTo(ring.digest("v1",request("update","POST","/api/v1/b","private-fixture")));
        assertThat(original.toString()).doesNotContain("private-fixture");
    }
    @Test void shortKeysFailAndRetainedOldKeyStillMatchesAfterRotation() {
        assertThatThrownBy(()->new HmacKeyRing("short",Map.of("short",new byte[8]))).isInstanceOf(IllegalArgumentException.class);
        byte[] old=secret(); var first=new HmacKeyRing("old",Map.of("old",old));
        var rotated=new HmacKeyRing("new",Map.of("old",old,"new",secret()));
        var req=request("create","POST","/api/v1/a","{}");
        assertThat(rotated.digest("old",req)).isEqualTo(first.digest("old",req));
        assertThatThrownBy(()->rotated.digest("missing",req)).isInstanceOf(com.adgn.backend.core.transaction.PersistenceFailure.class);
    }
    @Test void sensitiveNestedResponseAndUnsafeLocationAreRejectedBeforePersistence() {
        assertThatThrownBy(()->new SafeResult(201,"{\"nested\":[{\"temporaryPassword\":\"fixture\"}]}","/api/v1/a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SafeResult(201,"{}","https://untrusted.invalid/a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SafeResult(204,"{}",null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ambiguousOrTrailingJsonCannotHideSensitiveResponseFields() {
        assertThatThrownBy(()->new SafeResult(200,"{\"value\":1,\"value\":2}",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SafeResult(200,"{} {\"password\":\"fixture\"}",null)).isInstanceOf(IllegalArgumentException.class);
    }
}
