package com.adgn.backend.core.idempotency;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Locale;
public record SafeResult(int status,String body,String location) {
    private static final JsonMapper JSON=JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    public SafeResult {
        if (status<200 || status>=500 || status==401 || status==403 || status==429
                || (status==204)!=(body==null)) throw new IllegalArgumentException("Invalid safe response");
        if (location!=null && (!location.startsWith("/api/v1/") || location.length()>300
                || location.contains("\\") || location.contains("?") || location.contains("#")
                || location.contains("//") || location.contains("%") || location.contains("/..")
                || location.chars().anyMatch(Character::isISOControl))) throw new IllegalArgumentException("Invalid safe location");
        if (body!=null) {
            try { var node=JSON.readTree(body); if(node==null) throw new IllegalArgumentException(); rejectSecrets(node); body=JSON.writeValueAsString(node); }
            catch (RuntimeException failure) { throw new IllegalArgumentException("Unsafe response projection"); }
        }
    }
    private static void rejectSecrets(JsonNode node) {
        if (node.isObject()) for (var field:node.properties()) {
            String name=field.getKey().toLowerCase(Locale.ROOT).replace("_","").replace("-","");
            if (name.contains("password") || name.contains("token") || name.contains("secret") || name.contains("credential")
                    || name.contains("hash") || name.equals("authorization") || name.equals("cookie")) throw new IllegalArgumentException();
            rejectSecrets(field.getValue());
        }
        else if (node.isArray()) for(var value:node) rejectSecrets(value);
    }
    @Override public String toString() { return "SafeResult[status="+status+",redacted]"; }
}
