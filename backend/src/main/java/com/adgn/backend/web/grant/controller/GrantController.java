package com.adgn.backend.web.grant.controller;

import com.adgn.backend.grant.GrantQueries;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.scope.service.ManagementContextResolver;
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
public class GrantController {
    private final GrantQueries queries;
    public GrantController(GrantQueries queries){this.queries=queries;}
    @PostMapping("/grant-baselines")
    public ResponseEntity<?> baselines(@AuthenticationPrincipal Jwt token,@RequestBody GrantQueries.BaselineQuery input,HttpServletRequest request)throws Exception{return ok(queries.baselines(token,selection(request),input));}
    @PostMapping("/source-baselines")
    public ResponseEntity<?> sources(@AuthenticationPrincipal Jwt token,@RequestBody GrantQueries.SourceQuery input,HttpServletRequest request)throws Exception{return ok(queries.sources(token,selection(request),input));}
    @GetMapping("/grants")
    public ResponseEntity<?> grants(@AuthenticationPrincipal Jwt token,HttpServletRequest request,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,
            @RequestParam(required=false)String employeeId,@RequestParam(required=false)String roleId,@RequestParam(required=false)String companyId,
            @RequestParam(required=false)String sourceDepartmentId,@RequestParam(required=false)String systemId,@RequestParam(required=false)String status)throws Exception{
        return ok(queries.list(token,selection(request),page,pageSize,new GrantQueries.Filter(employeeId,roleId,companyId,sourceDepartmentId,systemId,status)));
    }
    private static ManagementContextResolver.Selection selection(HttpServletRequest request){return new ManagementContextResolver.Selection(header(request,"X-Admin-Appointment-Id"),header(request,"X-Admin-Appointment-Version"),header(request,"X-Admin-Scope-Version"));}
    private static String header(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()>1)throw IdentityFailure.invalid();return values.isEmpty()?null:values.getFirst();}
    private static ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
