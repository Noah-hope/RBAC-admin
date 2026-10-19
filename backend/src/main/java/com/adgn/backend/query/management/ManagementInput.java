package com.adgn.backend.query.management;

import com.adgn.backend.identity.security.IdentityFailure;
import java.util.Set;
import java.util.UUID;

/** Input rules shared by the system/role management projections. */
public final class ManagementInput {
    private ManagementInput() { }
    public static final long MAX_VERSION=9007199254740991L;
    public static String text(String value,int min,int max) {
        if(value==null)throw IdentityFailure.invalid();
        String result=value.strip();int length=result.codePointCount(0,result.length());
        if(length<min || length>max)throw IdentityFailure.invalid();
        return result;
    }
    public static String identifier(String value) {
        String result=text(value,1,64);
        if(!result.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))throw IdentityFailure.invalid();
        return result;
    }
    public static long version(Long value) {
        if(value==null || value<0 || value>MAX_VERSION)throw IdentityFailure.invalid();
        return value;
    }
    public static long number(String value,long fallback) {
        if(value==null)return fallback;
        try {
            if(!value.matches("[0-9]+"))throw new IllegalArgumentException();
            long n=Long.parseLong(value);if(n<1)throw new IllegalArgumentException();return n;
        } catch(RuntimeException invalid){throw IdentityFailure.invalid();}
    }
    public record Pagination(long page,int pageSize,long offset) { }
    public static Pagination pagination(String page,String size) {
        long p=number(page,1),s=number(size,20);
        if(!Set.of(10L,20L,50L,100L).contains(s))throw IdentityFailure.invalid();
        try{return new Pagination(p,(int)s,Math.multiplyExact(p-1,s));}
        catch(ArithmeticException invalid){throw IdentityFailure.invalid();}
    }
    public static UUID key(String value) {
        try{UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return id;}
        catch(RuntimeException invalid){throw IdentityFailure.invalid();}
    }
    public static Boolean bool(String value) {
        if(value==null)return null;
        if(!Set.of("true","false").contains(value))throw IdentityFailure.invalid();
        return Boolean.valueOf(value);
    }
    public static String like(String value) {return value.replace("=","==").replace("%","=%").replace("_","=_")+"%";}
}
