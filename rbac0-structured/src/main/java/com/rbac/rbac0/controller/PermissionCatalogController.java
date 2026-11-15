package com.rbac.rbac0.controller;

import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.PermissionCatalogService;
import org.springframework.web.bind.annotation.*;

@RestController
public class PermissionCatalogController {
  private final PermissionCatalogService service;

  public PermissionCatalogController(PermissionCatalogService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/permission-catalog")
  public PermissionCatalogService.Catalog catalog(@RequestAttribute(ACTOR) SessionUser actor) {
    return service.catalog(actor);
  }
}
