package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.auth.MyPermissionQueryService;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.interfaces.rest.vo.MyPermissionsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 「我的权限」接口：当前用户按应用查看已获菜单、按钮及其配置来源。 */
@RestController
@RequiredArgsConstructor
public class MyPermissionController {

  private final MyPermissionQueryService permissions;

  /** 查看当前用户的权限。 */
  @GetMapping("/api/v1/me/permissions")
  public MyPermissionsVO mine(
      @RequestAttribute(OPERATOR)
      Operator operator) {
    return MyPermissionsVO.from(permissions.mine(operator));
  }
}
