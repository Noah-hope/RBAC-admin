package com.adgn.backend;

import com.adgn.backend.core.idempotency.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IdempotencyValueTest {
    @Test void cachedResultsRejectTransientStatusesAndInconsistentEmptyResponses() {
        for (int status : new int[]{199, 401, 403, 429, 500, 503})
            assertThatThrownBy(() -> new SafeResult(status, "{}", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SafeResult(200, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SafeResult(204, null, null).body()).isNull();
        assertThat(new SafeResult(409, " { \"code\" : \"VERSION_CONFLICT\" } ", null).body())
                .isEqualTo("{\"code\":\"VERSION_CONFLICT\"}");
    }

    @Test void cachedResultsRejectNestedSecretAliasesAndMalformedJson() {
        for (String name : List.of("access_token", "client-secret", "credential", "passwordHash", "Authorization", "Cookie")) {
            String body="{\"items\":[{\""+name+"\":\"private-fixture\"}]}";
            assertThatThrownBy(() -> new SafeResult(200, body, null)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String body : List.of("{", "{\"x\":}"))
            assertThatThrownBy(() -> new SafeResult(200, body, null)).isInstanceOf(IllegalArgumentException.class);
        var safe = new SafeResult(200, "{\"items\":[1,true,null,{\"name\":\"safe\"}]}", null);
        assertThat(safe.body()).isEqualTo("{\"items\":[1,true,null,{\"name\":\"safe\"}]}");
        assertThat(safe.toString()).doesNotContain("safe\"");
    }

    @Test void emptyJsonDocumentCannotBecomeReplayableSuccess() {
        for (String body : List.of("", " \t\r\n"))
            assertThatThrownBy(() -> new SafeResult(200, body, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SafeResult(200,"null",null).body()).isEqualTo("null");
    }

    @Test void replayLocationRejectsQueriesFragmentsTraversalAndControlCharacters() {
        for (String path : List.of("/api/v1/a?x=1", "/api/v1/a#x", "/api/v1/a\\b", "/api/v1//a",
                "/api/v1/%2e%2e", "/api/v1/../a", "/api/v1/a\r\nLocation:x", "/api/v1/"+"a".repeat(300)))
            assertThatThrownBy(() -> new SafeResult(201, "{}", path)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SafeResult(201,"{}","/api/v1/roles/role-1").location()).isEqualTo("/api/v1/roles/role-1");
    }

    @Test void requestsRejectAmbiguousMetadataAndDefensivelyOwnCanonicalBytes() {
        byte[] bytes="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request=new IdempotencyRequest(principal,"create","POST","/api/v1/a",key,bytes);
        bytes[0]='X';request.canonicalBody()[0]='Y';
        assertThat(request.canonicalBody()).containsExactly((byte)'{',(byte)'}');
        for (String path : new String[]{null, "/wrong", "/api/v1/a?x=1", "/api/v1/a#x", "/api/v1/a\\b",
                "/api/v1//a", "/api/v1/%41", "/api/v1/a/../b", "/api/v1/a/..", "/api/v1/./a", "/api/v1/a\n", "/api/v1/"+"a".repeat(300)})
            assertThatThrownBy(() -> request("create","POST",path,"{}")).isInstanceOf(IllegalArgumentException.class);
        for (String method : new String[]{null,"GET","post"})
            assertThatThrownBy(() -> request("create",method,"/api/v1/a","{}")).isInstanceOf(IllegalArgumentException.class);
        for (String operation : new String[]{null,"1create","a".repeat(101)})
            assertThatThrownBy(() -> request(operation,"POST","/api/v1/a","{}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void keyRingRefusesIncompleteConfigurationsAndCallerMutationCannotChangeDigests() {
        assertThatThrownBy(() -> new HmacKeyRing(null,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacKeyRing("v1",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacKeyRing("v1",Map.of())).isInstanceOf(IllegalArgumentException.class);
        for (String id : new String[]{null,"invalid.key"}) {
            var entries=new HashMap<String,byte[]>();entries.put("v1",new byte[32]);entries.put(id,new byte[32]);
            assertThatThrownBy(() -> new HmacKeyRing("v1",entries)).isInstanceOf(IllegalArgumentException.class);
        }
        var missing=new HashMap<String,byte[]>();missing.put("v1",null);
        assertThatThrownBy(() -> new HmacKeyRing("v1",missing)).isInstanceOf(IllegalArgumentException.class);
        byte[] keyBytes=new byte[32];for(int i=0;i<32;i++)keyBytes[i]=(byte)i;
        var keys=new HashMap<String,byte[]>();keys.put("v1",keyBytes);var ring=new HmacKeyRing("v1",keys);
        Arrays.fill(keyBytes,(byte)99);keys.clear();
        assertThat(HexFormat.of().formatHex(ring.digest("v1",request("updateFixture","POST","/api/v1/fixture","{\"value\":1}"))))
                .isEqualTo("fb88e432a5709917e2e6200ed8a020e3512107ee49a5bce2ecc772baa885de07");
        assertThat(ring.currentId()).isEqualTo("v1");
        assertThat(ring.toString()).doesNotContain("99");
    }
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
    @Test void accountPasswordChangeFlagIsSafeOnlyAsAnExactBooleanField() {
        assertThatCode(()->new SafeResult(201,"{\"mustChangePassword\":true}","/api/v1/admin/accounts/demo"))
                .doesNotThrowAnyException();
        for(String value:List.of("\"private-fixture\"","null","{}","[]","1"))
            assertThatThrownBy(()->new SafeResult(200,"{\"mustChangePassword\":"+value+"}",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SafeResult(200,"{\"must_change_password\":true}",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SafeResult(200,"{\"mustChangePassword\":true,\"nested\":{\"password\":\"private-fixture\"}}",null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
