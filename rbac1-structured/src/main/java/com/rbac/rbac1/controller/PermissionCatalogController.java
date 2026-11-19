package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac1.service.PermissionCatalogService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.PermissionCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 权限目录接口：按应用分组列出全部权限，供角色编辑和授权页面选择。 */
@RestController
@RequiredArgsConstructor
public class PermissionCatalogController {

  private final PermissionCatalogService service;

  /** 返回完整权限目录，不分页。 */
  @GetMapping("/api/v1/permission-catalog")
  public PermissionCatalogVO catalog(
      @RequestAttribute(ACTOR)
      SessionUser actor) {
    return service.catalog(actor);
  }
}
