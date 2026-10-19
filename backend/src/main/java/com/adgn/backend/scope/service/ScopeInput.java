package com.adgn.backend.scope.service;

import com.adgn.backend.identity.security.IdentityFailure;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Set;

public final class ScopeInput {
    private ScopeInput() { }
    public static String identifier(String value){
        if(value==null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw IdentityFailure.invalid();
        return value;
    }
    public static long version(String value){
        try{if(value==null || !value.matches("[0-9]+"))throw IdentityFailure.invalid();long n=Long.parseLong(value);if(n>9007199254740991L)throw IdentityFailure.invalid();return n;}
        catch(NumberFormatException e){throw IdentityFailure.invalid();}
    }
    public record Window(long page,int size,long offset){
        public boolean beyondJpaOffset(){return offset>Integer.MAX_VALUE;}
        public Pageable pageable(){return PageRequest.of(Math.toIntExact(page-1),size);}
    }
    public static Window page(String page,String size){
        long p=page==null?1:version(page),s=size==null?20:version(size);
        if(p<1 || !Set.of(10L,20L,50L,100L).contains(s))throw IdentityFailure.invalid();
        try{return new Window(p,(int)s,Math.multiplyExact(p-1,s));}catch(ArithmeticException e){throw IdentityFailure.invalid();}
    }
}
