package com.adgn.backend.web.management;

import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.identity.security.IdentityFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

/** HTTP-only adapter shared by B's controllers. */
public final class ManagementHeaders {
    private ManagementHeaders() { }
    public static ManagementAccess.Context context(HttpServletRequest request){
        return new ManagementAccess.Context(header(request,"X-Admin-Appointment-Id"),header(request,"X-Admin-Appointment-Version"),header(request,"X-Admin-Scope-Version"));
    }
    private static String header(HttpServletRequest request,String name){
        var values=Collections.list(request.getHeaders(name));
        if(values.size()>1)throw IdentityFailure.invalid();
        return values.isEmpty()?null:values.getFirst();
    }
}
