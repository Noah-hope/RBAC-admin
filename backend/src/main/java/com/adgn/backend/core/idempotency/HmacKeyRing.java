package com.adgn.backend.core.idempotency;
import java.util.Map;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.adgn.backend.core.transaction.PersistenceFailure;
public final class HmacKeyRing {
    private final String current;
    private final Map<String,byte[]> keys;
    public HmacKeyRing(String current,Map<String,byte[]> keys) {
        if (current == null || keys == null || !keys.containsKey(current)) throw invalid();
        var copy=new HashMap<String,byte[]>();
        keys.forEach((id,key)->{
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || key == null || key.length<32) throw invalid();
            copy.put(id,key.clone());
        });
        this.current=current; this.keys=Map.copyOf(copy);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid idempotency key configuration"); }
    public String currentId() { return current; }
    public byte[] digest(String keyId,IdempotencyRequest request) {
        var secret=keys.get(keyId);
        if (secret==null) throw new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,null);
        try {
            var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            mac.update((byte)1);
            for (var field:new byte[][] {request.operationId().getBytes(StandardCharsets.UTF_8),request.method().getBytes(StandardCharsets.UTF_8),
                    request.path().getBytes(StandardCharsets.UTF_8),request.canonicalBody()}) {
                mac.update(ByteBuffer.allocate(4).putInt(field.length).array()); mac.update(field);
            }
            return mac.doFinal();
        } catch (java.security.GeneralSecurityException failure) {
            throw new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,null);
        }
    }
    @Override public String toString() { return "HmacKeyRing[redacted]"; }
}
