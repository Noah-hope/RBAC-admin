package com.adgn.backend.query.change;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import java.time.*;
import java.util.*;

public final class QueryInput {
    private QueryInput() { }
    public static String id(Map<String,String> q,String name){return q.containsKey(name)?ManagementInput.identifier(q.get(name)):null;}
    public static String text(Map<String,String> q,String name,int max){return q.containsKey(name)?ManagementInput.text(q.get(name),1,max):null;}
    public static String enumeration(Map<String,String> q,String name,Set<String> allowed){String value=q.get(name);if(value!=null&&!allowed.contains(value))throw IdentityFailure.invalid();return value;}
    public static Instant time(Map<String,String> q,String name){if(!q.containsKey(name))return null;try{return OffsetDateTime.parse(q.get(name)).toInstant();}catch(DateTimeException bad){throw IdentityFailure.invalid();}}
    public static void allowed(Map<String,String> q,Set<String> names){if(!names.containsAll(q.keySet()))throw IdentityFailure.invalid();}
}
