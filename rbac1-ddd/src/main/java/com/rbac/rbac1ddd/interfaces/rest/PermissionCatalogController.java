package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.app.PermissionCatalogQueryService;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.interfaces.rest.vo.PermissionCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 权限目录接口：管理员读取按应用分组的完整菜单、按钮目录，用于配置角色。 */
@RestController
@RequiredArgsConstructor
public class PermissionCatalogController {

  private final PermissionCatalogQueryService catalog;

  /** 读取角色配置权限目录。 */
  @GetMapping("/api/v1/permission-catalog")
  public PermissionCatalogVO catalog(
      @RequestAttribute(OPERATOR)
      Operator operator) {
    return PermissionCatalogVO.from(catalog.catalog(operator));
  }
}
