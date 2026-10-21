package com.adgn.backend.change;

import org.erdtman.jcs.JsonCanonicalizer;
import java.security.MessageDigest;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** RFC8785原字节；显式拒绝I-JSON不允许的重复键与孤立代理字符。 */
public final class CanonicalPayloadCodec {
    private static final JsonMapper STRICT=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private CanonicalPayloadCodec(){}
    public static byte[] canonical(String text){
        try{validate(STRICT.readTree(text));return new JsonCanonicalizer(text).getEncodedUTF8();}
        catch(Exception invalid){throw new IllegalArgumentException("Invalid canonical JSON input");}
    }
    private static void validate(JsonNode n){
        if(n==null)throw new IllegalArgumentException();
        if(n.isObject()){for(String key:n.propertyNames()){unicode(key);validate(n.get(key));}}
        else if(n.isArray()){for(var item:n)validate(item);}
        else if(n.isString())unicode(n.stringValue());
        else if(n.isNumber()&&!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException();
    }
    private static void unicode(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException();}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();}}
    public static byte[] digest(byte[] input){try{return MessageDigest.getInstance("SHA-256").digest(input);}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
