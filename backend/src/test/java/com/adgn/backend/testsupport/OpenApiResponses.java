package com.adgn.backend.testsupport;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Test-only response boundary validation against the repository contract, with no response secrets in evidence. */
public final class OpenApiResponses {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String,Set<Integer>> COVERAGE = new TreeMap<>();
    private static final List<String> VIOLATIONS = new ArrayList<>();
    private static OpenApiResponses instance;
    private final JsonNode document;
    private final List<Route> routes = new ArrayList<>();
    private record Route(String method, Pattern path, JsonNode operation) {}
    public OpenApiResponses(JsonNode document) {
        this.document=document;
        for(var entry:document.path("paths").properties()) {
            String pattern=Arrays.stream(entry.getKey().split("/",-1)).map(p->p.startsWith("{")?"[^/]+":Pattern.quote(p)).collect(java.util.stream.Collectors.joining("/"));
            for(var op:entry.getValue().properties()) if(Set.of("get","post","put","delete","patch").contains(op.getKey()))
                routes.add(new Route(op.getKey().toUpperCase(Locale.ROOT),Pattern.compile(pattern),op.getValue()));
        }
    }
    private static synchronized OpenApiResponses contract() {
        if(instance==null) {
            var yaml=new YamlMapFactoryBean(); yaml.setResources(new FileSystemResource("../docs/03-api/openapi.yaml"));
            instance=new OpenApiResponses(JSON.valueToTree(yaml.getObject()));
        }
        return instance;
    }
    public static <T> HttpResponse<T> check(HttpResponse<T> response) {
        if(!"true".equals(System.getenv("RBAC_CONTRACT_CHECK"))) return response;
        String path=response.request().uri().getPath();
        if(!path.startsWith("/api/v1/")) return response;
        var c=contract();
        var route=c.routes.stream().filter(r->r.method.equals(response.request().method())&&r.path.matcher(path).matches()).findFirst();
        // Negative tests deliberately request unknown paths; they are not declared operations.
        if(route.isEmpty()) return response;
        String op=route.get().operation.path("operationId").stringValue();
        try {
            c.response(route.get().operation,response.statusCode(),response.headers().firstValue("Content-Type").orElse(""),
                response.headers().firstValue("Cache-Control").orElse(""),response.body() instanceof byte[] b?new String(b,java.nio.charset.StandardCharsets.UTF_8):String.valueOf(response.body()));
            record(op,response.statusCode(),null);
        } catch(AssertionError failure) {
            record(op,response.statusCode(),failure.getMessage()); throw new AssertionError(op+": "+failure.getMessage());
        }
        return response;
    }
    private static synchronized void record(String operation,int status,String error) {
        if(error==null) COVERAGE.computeIfAbsent(operation,k->new TreeSet<>()).add(status);
        else VIOLATIONS.add(operation+" status="+status+" "+error);
        try { Files.writeString(Path.of(".tmp/be33-contract-coverage.json"),JSON.writeValueAsString(Map.of("operations",COVERAGE,"violations",VIOLATIONS))); }
        catch(Exception e) { throw new IllegalStateException("Cannot persist contract coverage",e); }
    }
    public void response(JsonNode operation,int status,String media,String cache,String body) {
        var response=resolve(operation.path("responses").path(String.valueOf(status)));
        require(!response.isMissingNode(),"undeclared HTTP status "+status);
        var content=response.path("content");
        if(content.isMissingNode()||content.isEmpty()) {require(body.isEmpty(),"unexpected response body");return;}
        String type=media.split(";",2)[0].trim();
        require(content.has(type),"undeclared media type "+type);
        if(response.path("headers").has("Cache-Control")) require(cache.equals("no-store"),"missing no-store");
        JsonNode value;
        try {value=JSON.readTree(body);}catch(Exception e){throw new AssertionError("invalid JSON");}
        schema(content.path(type).path("schema"),value,"$");
    }
    private JsonNode resolve(JsonNode node) {
        if(node.has("$ref")) {String ref=node.path("$ref").stringValue();require(ref.startsWith("#/"),"external schema reference");return resolve(document.at(ref.substring(1)));}
        return node;
    }
    public void schema(JsonNode raw,JsonNode value,String path) {
        var s=resolve(raw);require(!s.isMissingNode(),path+" missing schema");
        if(s.isBoolean()){require(s.booleanValue(),path+" forbidden");return;}
        for(String union:List.of("oneOf","anyOf")) if(s.has(union)) {
            int matched=0;for(var branch:s.path(union)){try{schema(branch,value,path);matched++;}catch(AssertionError ignored){}}
            require(union.equals("oneOf")?matched==1:matched>0,path+" "+union+" matches="+matched);
        }
        for(var branch:s.path("allOf")) schema(branch,value,path);
        if(s.has("type")) {
            var t=s.path("type");boolean match=false;
            for(var type:t.isArray()?t:JSON.valueToTree(List.of(t.stringValue()))) match|=type(type.stringValue(),value);
            require(match,path+" wrong type");
        }
        if(s.has("enum")){boolean match=false;for(var e:s.path("enum"))match|=e.equals(value);require(match,path+" outside enum");}
        if(s.has("const"))require(s.path("const").equals(value),path+" wrong const");
        if(value.isObject()) {
            for(var name:s.path("required")) require(value.has(name.stringValue()),path+" missing "+name.stringValue());
            var props=s.path("properties");
            for(var e:value.properties()) {
                if(props.has(e.getKey())) schema(props.path(e.getKey()),e.getValue(),path+"."+e.getKey());
                else if(s.has("additionalProperties")) {
                    var extra=s.path("additionalProperties");
                    if(extra.isBoolean())require(extra.booleanValue(),path+" unexpected "+e.getKey());
                    else schema(extra,e.getValue(),path+"."+e.getKey());
                }
            }
        }
        if(value.isArray()) {
            bound(s,"minItems",value.size(),true,path);bound(s,"maxItems",value.size(),false,path);
            if(s.path("uniqueItems").asBoolean(false)){var unique=new HashSet<JsonNode>();for(var item:value)require(unique.add(item),path+" duplicate item");}
            if(s.has("items")) for(int i=0;i<value.size();i++)schema(s.path("items"),value.get(i),path+"["+i+"]");
        }
        if(value.isString()) {
            String str=value.stringValue();int length=str.codePointCount(0,str.length());bound(s,"minLength",length,true,path);bound(s,"maxLength",length,false,path);
            if(s.has("pattern"))require(Pattern.compile(s.path("pattern").stringValue()).matcher(str).find(),path+" pattern mismatch");
            if(s.path("format").asString("").equals("date-time"))try{OffsetDateTime.parse(str);}catch(Exception e){throw new AssertionError(path+" invalid date-time");}
            if(s.path("format").asString("").equals("uuid"))try{UUID.fromString(str);}catch(Exception e){throw new AssertionError(path+" invalid uuid");}
            if(s.path("format").asString("").startsWith("uri"))try{URI.create(str);}catch(Exception e){throw new AssertionError(path+" invalid URI");}
        }
        if(value.isNumber()){bound(s,"minimum",value.doubleValue(),true,path);bound(s,"maximum",value.doubleValue(),false,path);}
    }
    private boolean type(String type,JsonNode v){return switch(type){case "null"->v.isNull();case "object"->v.isObject();case "array"->v.isArray();case "string"->v.isString();case "integer"->v.isIntegralNumber();case "number"->v.isNumber();case "boolean"->v.isBoolean();default->throw new AssertionError("Unsupported type "+type);};}
    private void bound(JsonNode s,String key,double actual,boolean lower,String path){if(s.has(key))require(lower?actual>=s.path(key).doubleValue():actual<=s.path(key).doubleValue(),path+" violates "+key);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
