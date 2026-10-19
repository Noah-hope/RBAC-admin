package com.adgn.backend.web.people.controller;

import com.adgn.backend.people.PeopleQueries;
import com.adgn.backend.identity.security.IdentityFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class PeopleController {
    private final PeopleQueries service;
    public PeopleController(PeopleQueries service){this.service=service;}
    @GetMapping({"/companies","/departments","/positions","/employees"})
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt token,HttpServletRequest request,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,
            @RequestParam(required=false)String companyId,@RequestParam(required=false)String departmentId,
            @RequestParam(required=false)String status,@RequestParam(required=false)String q)throws Exception{
        String path=request.getServletPath();String resource=path.substring(path.lastIndexOf('/')+1);
        return ok(service.list(token,context(request),resource,null,new PeopleQueries.Filter(page,pageSize,companyId,departmentId,status,q)));
    }
    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt token,@PathVariable String employeeId,HttpServletRequest request)throws Exception{
        return ok(service.get(token,context(request),employeeId));
    }
    @GetMapping("/employees/{employeeId}/employments")
    public ResponseEntity<?> jobs(@AuthenticationPrincipal Jwt token,@PathVariable String employeeId,HttpServletRequest request,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,
            @RequestParam(required=false)String companyId,@RequestParam(required=false)String departmentId,
            @RequestParam(required=false)String status)throws Exception{
        return ok(service.list(token,context(request),"employments",employeeId,new PeopleQueries.Filter(page,pageSize,companyId,departmentId,status,null)));
    }
    private static PeopleQueries.Context context(HttpServletRequest r){return new PeopleQueries.Context(header(r,"X-Admin-Appointment-Id"),header(r,"X-Admin-Appointment-Version"),header(r,"X-Admin-Scope-Version"));}
    private static String header(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));if(values.size()>1)throw IdentityFailure.invalid();return values.isEmpty()?null:values.getFirst();}
    private static ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
