package com.adgn.backend.web.audit.controller;

import com.adgn.backend.identity.security.IdentityFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/** 读取接口的查询参数不接受同名多值，避免筛选条件被不同层解释为不同范围。 */
public final class QueryParameters {
    private QueryParameters() { }
    public static Map<String,String> from(HttpServletRequest request){var values=new TreeMap<String,String>();request.getParameterMap().forEach((key,items)->{if(items.length!=1)throw IdentityFailure.invalid();values.put(key,items[0]);});return Map.copyOf(values);}
}
