package com.adgn.backend.web.management;

import com.adgn.backend.query.management.ManagementAccess;
import jakarta.servlet.http.HttpServletRequest;

/** HTTP-only adapter shared by B's controllers. */
public final class ManagementHeaders {
    private ManagementHeaders() { }
    public static ManagementAccess.Context context(HttpServletRequest request){
        return new ManagementAccess.Context(request.getHeader("X-Admin-Appointment-Id"),request.getHeader("X-Admin-Appointment-Version"),request.getHeader("X-Admin-Scope-Version"));
    }
}
