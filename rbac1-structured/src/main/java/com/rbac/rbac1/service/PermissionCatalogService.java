package com.rbac.rbac1.service;

import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.vo.CatalogApplicationVO;
import com.rbac.rbac1.vo.PermissionCatalogVO;
import com.rbac.rbac1.vo.PermissionSummaryVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限目录查询：按应用分组列出全部权限，供角色编辑和授权页面选择。
 */
@Service
@RequiredArgsConstructor
public class PermissionCatalogService {

  private final ApplicationMapper apps;

  private final PermissionMapper permissions;

  /**
   * 返回完整的权限目录。目录是完整结构，不分页。
   *
   * @param actor 当前操作人，必须是总管理员或部门管理员
   * @return 按应用 ID 排序的应用及其权限
   */
  @Transactional(readOnly = true)
  public PermissionCatalogVO catalog(SessionUser actor) {
    DepartmentService.requireAdministrator(actor);
    List<Permission> all = permissions.findAll();
    return new PermissionCatalogVO(
        apps.findAll(Sort.by("id")).stream()
            .map(
                a ->
                    new CatalogApplicationVO(
                        a.getId(),
                        a.getName(),
                        a.getCode(),
                        all.stream()
                            .filter(p -> p.getApplicationId().equals(a.getId()))
                            .map(PermissionSummaryVO::of)
                            .toList()))
            .toList());
  }
}
