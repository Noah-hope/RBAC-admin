package com.rbac.rbac0.controller;
import com.rbac.rbac0.service.PermissionCatalogService;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import org.springframework.web.bind.annotation.*;
import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
@RestController
public class PermissionCatalogController {
    private final PermissionCatalogService service;
    public PermissionCatalogController(PermissionCatalogService service) {this.service=service;}
    @GetMapping("/api/v1/permission-catalog")
    public PermissionCatalogService.Catalog catalog(@RequestAttribute(ACTOR) SessionUser actor) {return service.catalog(actor);}
}
